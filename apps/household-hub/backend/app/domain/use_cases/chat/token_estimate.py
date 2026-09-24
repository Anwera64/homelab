# The hub has no tokenizer for the model it talks to, so it counts characters. Three per token is
# on the pessimistic side for English and markup - it overestimates, which is the safe direction
# for a budget that exists to keep the answer from being cut off.
CHARACTERS_PER_TOKEN = 3


def estimate_tokens(text: str) -> float:
    return len(text or "") / CHARACTERS_PER_TOKEN
