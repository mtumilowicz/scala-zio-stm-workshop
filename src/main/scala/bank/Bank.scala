package bank

import zio.stm.{STM, TRef}
import zio.{Console, IO, ZIO, ZIOAppDefault}

import scala.collection.mutable

class Bank {
  private val accounts = mutable.Map[Int, TRef[BankAccount]]()

  case class BankAccount(id: Int, balance: BigDecimal)

  case class NotEnoughBalance()

  case class AccountNotExists(id: Int)

  case class AccountAlreadyExists(id: Int)

  def getAccount(id: Int): IO[AccountNotExists, BankAccount] =
    ZIO.fromOption(accounts.get(id)).orElseFail(AccountNotExists(id))
      .flatMap(_.get.commit)

  def createBankAccount(id: Int, initialBalance: BigDecimal): IO[AccountAlreadyExists, TRef[BankAccount]] = for {
    _ <- ZIO.fail(AccountAlreadyExists(id)).when(accounts.contains(id))
    account <- TRef.make(BankAccount(id, initialBalance)).commit
    _ = accounts += (id -> account)
  } yield account

  def withdraw(accountId: Int, amount: BigDecimal): STM[NotEnoughBalance | AccountNotExists, Unit] = {
    for {
      accountRef <- STM.fromOption(accounts.get(accountId)).orElseFail(AccountNotExists(accountId))
      account <- accountRef.get
      _ <- STM.fail(new NotEnoughBalance).unless(account.balance >= amount)
      _ <- accountRef.update(_.copy(balance = account.balance - amount))
    } yield ()
  }

  def deposit(accountId: Int, amount: BigDecimal): STM[AccountNotExists, Unit] =
    accounts.get(accountId) match
      case Some(ref) => ref.update(account => account.copy(balance = account.balance + amount))
      case None => STM.fail(AccountNotExists(accountId))

  def transfer(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): IO[NotEnoughBalance | AccountNotExists, Unit] =
    (for {
      _ <- withdraw(fromAccountId, amount)
      _ <- deposit(toAccountId, amount)
    } yield ()).commit
}