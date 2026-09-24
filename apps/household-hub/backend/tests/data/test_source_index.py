import re
from typing import List

import pytest

from app.data.datasources.sqlite_source_index import SqliteSourceIndex, SqliteSourceIndexFactory
from app.domain.entities.source_passage import SourcePassage
from app.domain.exceptions import EmbeddingException


# Words that mean the same thing share a dimension, so a paraphrase lands next to what it paraphrases.
_CONCEPTS = {
    "detention": 0, "camps": 0, "camp": 0, "re": 0, "education": 0, "facilities": 0,
    "censorship": 1, "firewall": 1, "blocked": 1,
    "harvest": 2, "organ": 2, "organs": 2,
}


def _vector(text: str) -> List[float]:
    vector = [0.0, 0.0, 0.0, 0.01]
    for word in re.findall(r"\w+", text.lower()):
        if word in _CONCEPTS:
            vector[_CONCEPTS[word]] += 1.0
    return vector


class ConceptEmbedder:
    def __init__(self):
        self.calls: List[List[str]] = []

    async def embed(self, texts: List[str]) -> List[List[float]]:
        self.calls.append(list(texts))
        return [_vector(text) for text in texts]


class BrokenEmbedder:
    async def embed(self, texts: List[str]) -> List[List[float]]:
        raise EmbeddingException("bge-m3 is not loaded")


def _passage(id: str, text: str, title: str = "") -> SourcePassage:
    return SourcePassage(id=id, title=title or f"Source {id}", url=f"https://example.org/{id}", text=text)


@pytest.mark.asyncio
async def test_GIVEN_passages_WHEN_searched_by_keyword_THEN_the_one_with_the_words_comes_first():
    index = SqliteSourceIndex()
    await index.add([
        _passage("s1", "Hong Kong national security law arrests of activists"),
        _passage("s2", "The Great Firewall blocked foreign news sites"),
        _passage("s3", "Tibetan monasteries under surveillance"),
    ])

    found = await index.search("firewall news", k=2)

    assert found[0].id == "s2"


@pytest.mark.asyncio
async def test_GIVEN_a_paraphrase_WHEN_searched_with_an_embedder_THEN_it_is_found_though_no_word_matches():
    index = SqliteSourceIndex(embedder=ConceptEmbedder())
    await index.add([
        _passage("s1", "Satellite images show new re-education facilities in Xinjiang"),
        _passage("s2", "Hong Kong national security law arrests"),
    ])

    found = await index.search("detention camps", k=1)

    assert [p.id for p in found] == ["s1"]


@pytest.mark.asyncio
async def test_GIVEN_both_rankings_WHEN_searched_THEN_a_passage_both_agree_on_beats_one_only_keywords_like():
    index = SqliteSourceIndex(embedder=ConceptEmbedder())
    await index.add([
        # Keywords like this one best, meaning puts it last.
        _passage("s1", "camps camps camps summer camps for children"),
        # Both like this one: the word and the meaning.
        _passage("s2", "detention camps and re-education facilities"),
        _passage("s3", "trade tariffs on steel"),
    ])

    found = await index.search("detention camps", k=3)

    assert found[0].id == "s2"


@pytest.mark.asyncio
async def test_GIVEN_an_embedder_that_fails_WHEN_searched_THEN_keywords_still_find_it():
    index = SqliteSourceIndex(embedder=BrokenEmbedder())
    await index.add([_passage("s1", "organ harvesting reports"), _passage("s2", "steel tariffs")])

    found = await index.search("organ harvesting", k=1)

    assert [p.id for p in found] == ["s1"]


@pytest.mark.asyncio
async def test_GIVEN_a_query_with_fts_syntax_WHEN_searched_THEN_it_is_read_as_plain_words():
    index = SqliteSourceIndex()
    await index.add([_passage("s1", "Uyghur forced labour in cotton supply chains")])

    found = await index.search('forced "labour" AND (cotton OR NEAR -', k=1)

    assert [p.id for p in found] == ["s1"]


@pytest.mark.asyncio
async def test_GIVEN_the_same_id_twice_WHEN_added_THEN_it_is_kept_once_and_found_as_first_given():
    index = SqliteSourceIndex()
    await index.add([_passage("s1", "cotton supply chains")])
    await index.add([_passage("s1", "something else entirely")])

    found = await index.search("cotton", k=5)

    assert [(p.id, p.text) for p in found] == [("s1", "cotton supply chains")]


@pytest.mark.asyncio
async def test_GIVEN_nothing_matches_WHEN_searched_THEN_nothing_is_returned():
    index = SqliteSourceIndex()
    await index.add([_passage("s1", "cotton supply chains")])

    assert await index.search("tariffs", k=5) == []


@pytest.mark.asyncio
async def test_GIVEN_a_factory_WHEN_asked_twice_THEN_each_turn_gets_its_own_index():
    factory = SqliteSourceIndexFactory(embedder=None)
    first, second = factory.new(), factory.new()
    await first.add([_passage("s1", "cotton supply chains")])

    assert await second.search("cotton", k=5) == []
