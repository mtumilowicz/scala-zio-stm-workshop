package bank

import zio.{Console, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.stm.{STM, TRef, USTM, ZSTM}

object Bank {
  var accounts = Map[Int, TRef[BankAccount]]()

  case class BankAccount(id: Int, balance: Double)

  case class NotEnoughBalance()

  def createBankAccount(id: Int, initialBalance: Double): USTM[TRef[BankAccount]] = for {
    account <- TRef.make(BankAccount(id, initialBalance))
    _ = accounts = accounts + (id -> account)
  } yield account

  def withdraw(accountId: Int, amount: Double): STM[NotEnoughBalance, Unit] = {
    val accountRef = accounts(accountId)
    for {
      account <- accounts(accountId).get
      _ <- STM.fail(new NotEnoughBalance).unless(account.balance >= amount)
      _ <- accountRef.update(_.copy(balance = account.balance - amount))
    } yield ()
  }

  def deposit(accountId: Int, amount: Double): USTM[Unit] =
    accounts(accountId).update(account => account.copy(balance = account.balance + amount))

  def transfer(fromAccountId: Int, toAccountId: Int, amount: Double): STM[NotEnoughBalance, Unit] =
    for {
      _ <- withdraw(fromAccountId, amount)
      _ <- deposit(toAccountId, amount)
    } yield ()
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