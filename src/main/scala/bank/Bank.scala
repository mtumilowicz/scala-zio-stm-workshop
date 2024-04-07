package bank

import zio.{Console, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.stm.{STM, TRef, USTM, ZSTM}

class Bank {
  var accounts = Map[Int, TRef[BankAccount]]()

  case class BankAccount(id: Int, balance: BigDecimal)

  case class NotEnoughBalance()

  def createBankAccount(id: Int, initialBalance: BigDecimal): USTM[TRef[BankAccount]] = for {
    account <- TRef.make(BankAccount(id, initialBalance))
    _ = accounts = accounts + (id -> account)
  } yield account

  def withdraw(accountId: Int, amount: BigDecimal): STM[NotEnoughBalance, Unit] = {
    val accountRef = accounts(accountId)
    for {
      account <- accounts(accountId).get
      _ <- STM.fail(new NotEnoughBalance).unless(account.balance >= amount)
      _ <- accountRef.update(_.copy(balance = account.balance - amount))
    } yield ()
  }

  def deposit(accountId: Int, amount: BigDecimal): USTM[Unit] =
    accounts(accountId).update(account => account.copy(balance = account.balance + amount))

  def transfer(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): STM[NotEnoughBalance, Unit] =
    for {
      _ <- withdraw(fromAccountId, amount)
      _ <- deposit(toAccountId, amount)
    } yield ()
}

object BankSimulation extends ZIOAppDefault {
  val bank = new Bank
  val accountSimulation = for {
    _ <- bank.createBankAccount(1, 100.0).commit
    _ <- bank.createBankAccount(2, 200.0).commit
    _ <- bank.transfer(1, 2, 50.0).commit
    b1 <- bank.accounts(1).get.commit
    b2 <- bank.accounts(2).get.commit
    _ <- Console.printLine(s"b1: $b1")
    _ <- Console.printLine(s"b1: $b2")
  } yield ()

  override def run =
    accountSimulation
}