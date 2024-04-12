package bank

import zio._
import zio.test._
import zio.test.Assertion._

object BankSpec extends ZIOSpecDefault {

  def genAccountId: Gen[Any, AccountId] =
    Gen.int.map(AccountId(_))

  override def spec: Spec[TestEnvironment, Any] =
    suite("Bank")(
      test("balance of two accounts should remain consistent after transfers") {
        check(Gen.bigDecimal(0.01, 1.0), genAccountId, genAccountId) { (amount, accountId1, accountId2) =>
          for {
            bank <- Bank()
            account1 <- bank.createBankAccount(accountId1, 10000.0)
            account2 <- bank.createBankAccount(accountId2, 10000.0)

            _ <- ZIO.foreachParDiscard(1 to 1000) {
              _ =>
                bank.transfer(accountId1, accountId2, amount)
                  .option
            } <&> ZIO.foreachParDiscard(1 to 1000) {
              _ =>
                bank.transfer(accountId2, accountId1, amount)
                  .option
            }

            balance1 <- bank.getAccount(accountId1).map(_.balance)
            balance2 <- bank.getAccount(accountId2).map(_.balance)
          } yield assertTrue(balance1 + balance2 == 20000.0)
        }
      },
      test("result of successful transfers should be reflected in balances") {
        check(Gen.listOfN(100)(Gen.bigDecimal(0.01, 10)), genAccountId, genAccountId) { (amounts, accountId1, accountId2) =>
          for {
            bank <- Bank()
            account1 <- bank.createBankAccount(accountId1, 10000.0)
            account2 <- bank.createBankAccount(accountId2, 10000.0)

            _ <- ZIO.foreachParDiscard(amounts) { amount =>
              bank.transfer(accountId1, accountId2, amount)
                .option
            }

            balance1 <- bank.getAccount(accountId1).map(_.balance)
            balance2 <- bank.getAccount(accountId2).map(_.balance)
            delta = amounts.sum
          } yield assertTrue(
            balance1 == 10000 - delta,
            balance2 == 10000 + delta
          )
        }
      },
      test("result of failed transfers should not be reflected in balances") {
        check(Gen.listOfN(100)(Gen.bigDecimal(101, 1000)), genAccountId, genAccountId) { (amounts, accountId1, accountId2) =>
          for {
            bank <- Bank()
            account1 <- bank.createBankAccount(accountId1, 100)
            account2 <- bank.createBankAccount(accountId2, 100)

            _ <- ZIO.foreachParDiscard(amounts) { amount =>
              bank.transfer(accountId1, accountId2, amount)
                .option
            }

            balance1 <- bank.getAccount(accountId1).map(_.balance)
            balance2 <- bank.getAccount(accountId2).map(_.balance)
            delta = amounts.sum
          } yield assertTrue(
            balance1 == 100,
            balance2 == 100
          )
        }
      }
    )
}
