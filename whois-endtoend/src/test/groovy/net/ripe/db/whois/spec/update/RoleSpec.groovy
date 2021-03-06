package net.ripe.db.whois.spec.update

import net.ripe.db.whois.spec.BaseQueryUpdateSpec
import net.ripe.db.whois.spec.domain.Message

class RoleSpec extends BaseQueryUpdateSpec {

    @Override
    Map<String, String> getTransients() {
        [
                "RL": """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST
                """,
                "NO-MB-RL": """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                changed: dbtest@ripe.net 20121016
                source:  TEST
                """,
                "RL-NO-MB-PN": """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  NMP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST
                """,
                "NO-MB-PN": """\
                person:  No MB Person
                address: St James Street
                address: Burnley
                address: UK
                phone:   +44 282 420469
                nic-hdl: NMP1-TEST
                changed: denis@ripe.net 20121016
                source:  TEST
                """,
                "NO-MB-PN-MNT": """\
                mntner:      NO-MB-PN-MNT
                descr:       used to maintain other MNTNERs
                admin-c:     NMP1-TEST
                upd-to:      updto_owner@ripe.net
                mnt-nfy:     mntnfy_owner@ripe.net
                notify:      notify_owner@ripe.net
                auth:        MD5-PW \$1\$fyALLXZB\$V5Cht4.DAIM3vi64EpC0w/  #owner
                mnt-by:      OWNER-MNT
                referral-by: OWNER-MNT
                changed:     dbtest@ripe.net
                source:      TEST
                """,
                "RL2": """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: FR1-TEST
                tech-c:  FR1-TEST
                nic-hdl: FR2-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST
                """
        ]
    }

    def "delete non-existent role"() {
        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "FR1-TEST")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST
                delete:  testing

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message
        ack.failed

        ack.summary.nrFound == 1
        ack.summary.assertSuccess(0, 0, 0, 0, 0)
        ack.summary.assertErrors(1, 0, 0, 1)

        ack.countErrorWarnInfo(1, 0, 0)
        ack.errors.any { it.operation == "Delete" && it.key == "[role] FR1-TEST   First Role" }
        ack.errorMessagesFor("Delete", "[role] FR1-TEST   First Role") == [
                "Object [role] FR1-TEST First Role does not exist in the database"]

        queryNothing("-r -T role FR1-TEST")
    }

    def "delete role"() {
        given:
        def toDelete = getTransient("RL")
        syncUpdate(toDelete + "password: owner")

        expect:
        queryObject("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: toDelete + "delete: testing role delete\npassword: owner"
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 0, 0, 1, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 0, 0)
        ack.successes.any { it.operation == "Delete" && it.key == "[role] FR1-TEST   First Role" }

        queryObjectNotFound("-rBGT role FR1-TEST", "role", "FR1-TEST")
    }

    def "modify role no date"() {
        given:
        syncUpdate(getTransient("RL") + "password: owner")

        expect:
        queryObject("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 0, 1, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 0, 0)
        ack.successes.any { it.operation == "Modify" && it.key == "[role] FR1-TEST   First Role" }

        query_object_not_matches("-r -T role FR1-TEST", "role", "First Role", "20121016")
    }

    def "modify role add missing mnt-by"() {
        given:
        dbfixture(getTransient("NO-MB-RL"))

        expect:
        query_object_not_matches("-r -T role FR1-TEST", "role", "First Role", "mnt-by:")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 0, 1, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 0, 0)
        ack.successes.any { it.operation == "Modify" && it.key == "[role] FR1-TEST   First Role" }

        query_object_matches("-rBT role FR1-TEST", "role", "First Role", "mnt-by:\\s*OWNER-MNT")
    }

    def "modify role to change name"() {
        given:
        syncUpdate(getTransient("RL") + "password: owner")

        expect:
        queryObject("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    Second Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.failed
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(0, 0, 0, 0, 0)
        ack.summary.assertErrors(1, 0, 1, 0)

        ack.countErrorWarnInfo(1, 0, 0)
        ack.errors.any { it.operation == "Modify" && it.key == "[role] FR1-TEST   Second Role" }
        ack.errorMessagesFor("Modify", "[role] FR1-TEST   Second Role") ==
                ["Person/Role name cannot be changed automatically. Please create " +
                        "another Person/Role object and modify any references to the old " +
                        "object, then delete the old object"]
        ack.objErrorContains("Modify", "FAILED", "role", "FR1-TEST   Second Role",
                "Person/Role name cannot be changed automatically. Please create")

        queryObject("-rBT role FR1-TEST", "role", "First Role")
    }

    def "modify role to change name and object type to PERSON"() {
        given:
        syncUpdate(getTransient("RL") + "password: owner")

        expect:
        queryObject("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                person:  First Person
                address: St James Street
                address: Burnley
                address: UK
                phone:   +44
                e-mail:  dbtest@ripe.net
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.failed
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(0, 0, 0, 0, 0)
        ack.summary.assertErrors(1, 1, 0, 0)

        ack.countErrorWarnInfo(1, 0, 0)
        ack.errors.any { it.operation == "Create" && it.key == "[person] FR1-TEST   First Person" }
        ack.errorMessagesFor("Create", "[person] FR1-TEST   First Person") ==
                ["The nic-hdl \"FR1-TEST\" is not available"]

        queryObject("-rBT role FR1-TEST", "role", "First Role")
    }

    def "create role using previously deleted nic-hdl"() {
        given:
        syncUpdate(getTransient("RL") + "password: owner")
        queryObject("-r -T role FR1-TEST", "role", "First Role")
        syncUpdate(getTransient("RL") + "delete: testing\npassword: owner")

        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.failed
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(0, 0, 0, 0, 0)
        ack.summary.assertErrors(1, 1, 0, 0)

        ack.countErrorWarnInfo(1, 0, 0)
        ack.errors.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }
        ack.errorMessagesFor("Create", "[role] FR1-TEST   First Role") ==
                ["The nic-hdl \"FR1-TEST\" is not available"]

        queryObjectNotFound("-rBT person FR1-TEST", "role", "First Role")
    }

    // Modify a ROLE that references a PERSON with no mnt-by:
    def "modify role ref no mnt-by person"() {
        given:
        dbfixture(getTransient("NO-MB-PN"))
        dbfixture(getTransient("NO-MB-PN-MNT"))
        syncUpdate(getTransient("RL") + "password: owner")

        expect:
        queryObject("-r -T person NMP1-TEST", "person", "No MB Person")
        queryObject("-r -T role FR1-TEST", "role", "First Role")
        queryObject("-r -T mntner NO-MB-PN-MNT", "mntner", "NO-MB-PN-MNT")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: NMP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  NO-MB-PN-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 0, 1, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 2, 0)
        ack.successes.any { it.operation == "Modify" && it.key == "[role] FR1-TEST   First Role" }
        ack.warningSuccessMessagesFor("Modify", "[role] FR1-TEST   First Role") == [
                "Referenced person object NMP1-TEST from mntner: NO-MB-PN-MNT is missing mandatory attribute \"mnt-by:\"",
                "Referenced person object NMP1-TEST is missing mandatory attribute \"mnt-by:\""
        ]

        query_object_matches("-rBT role FR1-TEST", "role", "First Role", "admin-c:\\s*NMP1-TEST")
    }

    // Create a ROLE that references a PERSON with no mnt-by:
    def "create role ref no mnt-by person"() {
        given:
        dbfixture(getTransient("NO-MB-PN"))
        dbfixture(getTransient("NO-MB-PN-MNT"))

        expect:
        query_object_not_matches("-r -T person NMP1-TEST", "person", "No MB Person", "mnt-by:")
        queryObject("-r -T mntner NO-MB-PN-MNT", "mntner", "NO-MB-PN-MNT")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: NMP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  NO-MB-PN-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 1, 0, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 2, 0)
        ack.successes.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }
        ack.warningSuccessMessagesFor("Create", "[role] FR1-TEST   First Role") == [
                "Referenced person object NMP1-TEST from mntner: NO-MB-PN-MNT is missing mandatory attribute \"mnt-by:\"",
                "Referenced person object NMP1-TEST is missing mandatory attribute \"mnt-by:\""
        ]

        query_object_matches("-rBT role FR1-TEST", "role", "First Role", "admin-c:\\s*NMP1-TEST")
    }

    def "create role"() {
        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: TP1-TEST
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  owner-mnt
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 1, 0, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 0, 0)
        ack.successes.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }

        queryObject("-rBT role FR1-TEST", "role", "First Role")
    }

    def "create role with all optional and multiple and duplicate attrs"() {
        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                org:     ORG-OTO1-TEST
                address: UK
                address: UK
                phone:   +31 205354444
                phone:   +31(20)5354444
                phone:   +31205354444 ext. 429
                phone:   +31(20)5354444 ext. 429
                fax-no:  +31 205354444
                notify:  dbtest-nfy@ripe.net
                e-mail:  dbtest@ripe.net
                phone:   +31 205354444
                fax-no:  +31(20)5354444 ext. 429
                e-mail:  dbtest@ripe.net
                org:     ORG-OTO1-TEST
                org:     ORG-OTO1-TEST
                admin-c: TP3-TEST
                tech-c:  TP2-TEST
                admin-c: TP2-TEST
                changed: dbtest@ripe.net 20121016
                tech-c:  TP1-TEST
                nic-hdl: FR1-TEST
                remarks: test role object
                changed: dbtest@ripe.net 20121016
                remarks:
                remarks: test role object
                notify:  dbtest-nfy@ripe.net
                abuse-mailbox: dbtest-abuse@ripe.net
                mnt-by:  owner-mnt, owner3-mnt, owner2-mnt, owner2-mnt
                mnt-by:  owner-mnt, owner-mnt, owner-mnt, owner-mnt
                changed: dbtest@ripe.net 20121016
                source:  TEST
                notify:  dbtest2-nfy@ripe.net
                changed: dbtest@ripe.net 20121017

                password: owner3
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 1, 0, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 1, 0)
        ack.warningSuccessMessagesFor("Create", "[role] FR1-TEST   First Role") == [
                "There are no limits on queries for ROLE objects containing \"abuse-mailbox:\""]
        ack.successes.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }

        queryObject("-rBT role FR1-TEST", "role", "First Role")
    }

    def "create self referencing role"() {
        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: FR1-TEST
                tech-c:  FR1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  owner-mnt
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.summary.nrFound == 1
        ack.summary.assertSuccess(0, 0, 0, 0, 0)
        ack.summary.assertErrors(1, 1, 0, 0)

        ack.countErrorWarnInfo(2, 0, 0)
        ack.errors.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }
        ack.errorMessagesFor("Create", "[role] FR1-TEST   First Role") ==
                ["Self reference is not allowed for attribute type \"admin-c\"",
                        "Self reference is not allowed for attribute type \"tech-c\""]

        queryObjectNotFound("-rBT role FR1-TEST", "role", "First Role")
    }

    def "create circular referencing roles"() {
        given:
        def role1 = getTransient("RL")
        syncUpdate(role1 + "password: owner")
        def role2 = getTransient("RL2")
        syncUpdate(role2 + "password: owner")

        expect:
        queryObject("-r -T role FR1-TEST", "role", "First Role")
        queryObject("-rBT role FR2-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: FR2-TEST
                tech-c:  FR2-TEST
                nic-hdl: FR1-TEST
                mnt-by:  owner-mnt
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.success
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 0, 1, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 0, 0)
        ack.successes.any { it.operation == "Modify" && it.key == "[role] FR1-TEST   First Role" }

        queryObject("-rBT role FR1-TEST", "role", "First Role")
        queryObject("-rBT role FR2-TEST", "role", "First Role")
    }

    def "create role referencing non exist persons"() {
        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                admin-c: XX1-TEST
                tech-c:  YY1-TEST
                nic-hdl: FR1-TEST
                mnt-by:  OWNER-MNT
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.failed
        ack.summary.nrFound == 1
        ack.summary.assertSuccess(0, 0, 0, 0, 0)
        ack.summary.assertErrors(1, 1, 0, 0)

        ack.countErrorWarnInfo(2, 0, 0)
        ack.errors.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }
        ack.errorMessagesFor("Create", "[role] FR1-TEST   First Role") ==
                ["Unknown object referenced XX1-TEST",
                        "Unknown object referenced YY1-TEST"]

        queryObjectNotFound("-rBT person FR1-TEST", "role", "First Role")
    }

    def "create role with no admin-c, tech-c"() {
        given:

        expect:
        queryObjectNotFound("-r -T role FR1-TEST", "role", "First Role")

        when:
        def message = send new Message(
                subject: "",
                body: """\
                role:    First Role
                address: St James Street
                address: Burnley
                address: UK
                e-mail:  dbtest@ripe.net
                nic-hdl: FR1-TEST
                mnt-by:  owner-mnt
                changed: dbtest@ripe.net 20121016
                source:  TEST

                password: owner
                """.stripIndent()
        )

        then:
        def ack = ackFor message

        ack.summary.nrFound == 1
        ack.summary.assertSuccess(1, 1, 0, 0, 0)
        ack.summary.assertErrors(0, 0, 0, 0)

        ack.countErrorWarnInfo(0, 0, 0)
        ack.successes.any { it.operation == "Create" && it.key == "[role] FR1-TEST   First Role" }

        query_object_not_matches("-rBT role FR1-TEST", "role", "First Role", "admin-c:")
        query_object_not_matches("-rBT role FR1-TEST", "role", "First Role", "tech-c:")
    }

}
