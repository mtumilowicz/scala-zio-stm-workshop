package bank

import zio.{Console, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.stm.{TRef, USTM, ZSTM}

object Bank {
  var accounts = Map[Int, TRef[BankAccount]]()

  case class BankAccount(id: Int, balance: Double)
  def createBankAccount(id: Int, initialBalance: Double): USTM[TRef[BankAccount]] = for {
    account <- TRef.make(BankAccount(id, initialBalance))
    _ = accounts = accounts + (id -> account)
  } yield account

  def withdraw(accountId: Int, amount: Double) = {
    val accountRef = accounts(accountId)
    for {
      account <- accounts(accountId).get
      result <- if (account.balance >= amount) {
        accountRef.update(_.copy(balance = account.balance - amount)) *> ZSTM.succeed(true)
      } else ZSTM.succeed(false)
    } yield result
  }

  def deposit(accountId: Int, amount: Double): USTM[Unit] =
    accounts(accountId).update(account => account.copy(balance = account.balance + amount))

  def transfer(fromAccountId: Int, toAccountId: Int, amount: Double) =
    for {
      withdrawn <- withdraw(fromAccountId, amount)
      _         <- if (withdrawn) deposit(toAccountId, amount) else ZSTM.unit
    } yield withdrawn
}

object BankSimulation extends ZIOAppDefault {
  val accountSimulation = for {
    _ <- Bank.createBankAccount(1, 100.0).commit
    _ <- Bank.createBankAccount(2, 200.0).commit
    _ <- Bank.transfer(1, 2, 50.0).commit
    b1 <- Bank.accounts(1).get.commit
    b2 <- Bank.accounts(2).get.commit
    _ <- Console.printLine(s"b1: $b1")
    _ <- Console.printLine(s"b1: $b2")
  } yield ()

  override def run =
    accountSimulation
}