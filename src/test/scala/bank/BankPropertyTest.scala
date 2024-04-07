package bank

import zio._
import zio.test._
import zio.test.Assertion._

object BankPropertyTest extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("Bank")(
      test("balance of two accounts should remain consistent after many transfers") {
        check(Gen.int(1, 1000), Gen.int(1, 1000), Gen.bigDecimal(0.01, 10000.0)) { (accountId1, accountId2, amount) =>
          for {
            bank <- ZIO.succeed(new Bank)
            account1 <- bank.createBankAccount(accountId1, 10000.0).commit
            account2 <- bank.createBankAccount(accountId2, 10000.0).commit

            _ <- ZIO.foreachParDiscard(1 to 1000) {
              _ =>
                bank.transfer(accountId1, accountId2, amount)
                  .commit
                  .option
            }

            balance1 <- bank.accounts(accountId1).get.map(_.balance).commit
            balance2 <- bank.accounts(accountId2).get.map(_.balance).commit
          } yield assertTrue(balance1 + balance2 == 20000.0)
        }
      },
      test("result of successful transfers should be reflected in balances") {
        check(Gen.int(1, 10000), Gen.int(1, 10000), Gen.listOfN(100)(Gen.bigDecimal(0.01, 10))) { (accountId1, accountId2, amounts) =>
          for {
            bank <- ZIO.succeed(new Bank)
            account1 <- bank.createBankAccount(accountId1, 10000.0).commit
            account2 <- bank.createBankAccount(accountId2, 10000.0).commit

            _ <- ZIO.foreachParDiscard(amounts) { amount =>
              bank.transfer(accountId1, accountId2, amount)
                .commit
                .option
            }

            balance1 <- bank.accounts(accountId1).get.map(_.balance).commit
            balance2 <- bank.accounts(accountId2).get.map(_.balance).commit
            delta = amounts.sum
          } yield assertTrue(
            balance1 == 10000 - delta,
            balance2 == 10000 + delta
          )
        }
      },
      test("result of failed transfers should not be reflected in balances") {
        check(Gen.int(1, 10000), Gen.int(1, 10000), Gen.listOfN(100)(Gen.bigDecimal(101, 1000))) { (accountId1, accountId2, amounts) =>
          for {
            bank <- ZIO.succeed(new Bank)
            account1 <- bank.createBankAccount(accountId1, 100).commit
            account2 <- bank.createBankAccount(accountId2, 100).commit

            _ <- ZIO.foreachParDiscard(amounts) { amount =>
              bank.transfer(accountId1, accountId2, amount)
                .commit
                .option
            }

            balance1 <- bank.accounts(accountId1).get.map(_.balance).commit
            balance2 <- bank.accounts(accountId2).get.map(_.balance).commit
            delta = amounts.sum
          } yield assertTrue(
            balance1 == 100,
            balance2 == 100
          )
        }
      }
    )
}
