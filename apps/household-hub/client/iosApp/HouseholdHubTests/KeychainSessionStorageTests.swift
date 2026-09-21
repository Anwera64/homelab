

    /// The member the hub last named is kept beside the token, in the same item, and a relaunch
    /// reads them back — which is what keeps the profile from drawing an empty circle offline.
    func testSecondInstanceReadsTheStoredMember() {
        let storage = KeychainSessionProbe()
        storage.save(accessToken: "access-5", refreshToken: nil)

        storage.saveMember(id: "emma", name: "Emma Larsson")

        let reader = KeychainSessionProbe()
        XCTAssertEqual(reader.memberId(), "emma")
        XCTAssertEqual(reader.memberName(), "Emma Larsson")
    }

    /// A PIN change saves a fresh token and nothing else; the stored member must survive it.
    func testSavingOnlyATokenKeepsTheStoredMember() {
        let storage = KeychainSessionProbe()
        storage.save(accessToken: "access-6", refreshToken: nil)
        storage.saveMember(id: "emma", name: "Emma Larsson")

        storage.save(accessToken: "access-6-rotated", refreshToken: nil)

        XCTAssertEqual(KeychainSessionProbe().accessToken(), "access-6-rotated")
        XCTAssertEqual(KeychainSessionProbe().memberName(), "Emma Larsson")
    }

    func testClearRemovesTheStoredMember() {
        let storage = KeychainSessionProbe()
        storage.save(accessToken: "access-7", refreshToken: nil)
        storage.saveMember(id: "emma", name: "Emma Larsson")

        storage.clear()

        XCTAssertNil(storage.memberId())
        XCTAssertNil(KeychainSessionProbe().memberName())
    }
}
