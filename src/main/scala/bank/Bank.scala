package bank

import zio.stm.{STM, TMap, TRef, USTM}
import zio.{IO, UIO}

case class Account(id: Int, balance: BigDecimal)

class Bank(accountsRef: TMap[Int, TRef[Account]]) {

  case class NotEnoughBalance()

  case class AccountNotExists(id: Int)

  case class AccountAlreadyExists(id: Int)

  def getAccount(id: Int): IO[AccountNotExists, Account] =
    getAccountRef(id).flatMap(_.get).commit

  def createBankAccount(id: Int, initialBalance: BigDecimal): IO[AccountAlreadyExists, TRef[Account]] =
    (for {
      _ <- STM.fail(AccountAlreadyExists(id)).whenSTM(contains(id))
      account <- TRef.make(Account(id, initialBalance))
      _ <- accountsRef.put(id, account)
    } yield account).commit

  def transfer(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): IO[NotEnoughBalance | AccountNotExists, Unit] =
    (for {
      _ <- withdraw(fromAccountId, amount)
      _ <- deposit(toAccountId, amount)
    } yield ()).commit

  private def contains(id: Int): USTM[Boolean] =
    accountsRef.contains(id)

  private def getAccountRef(id: Int): STM[AccountNotExists, TRef[Account]] = for {
    account <- accountsRef.get(id)
    account <- STM.fromOption(account).orElseFail(AccountNotExists(id))
  } yield account

  private def withdraw(accountId: Int, amount: BigDecimal): STM[NotEnoughBalance | AccountNotExists, Unit] = {
    for {
      accountRef <- getAccountRef(accountId)
      account <- accountRef.get
      _ <- STM.fail(new NotEnoughBalance).unless(account.balance >= amount)
      _ <- accountRef.update(_.copy(balance = account.balance - amount))
    } yield ()
  }

  private def deposit(accountId: Int, amount: BigDecimal): STM[AccountNotExists, Unit] =
    getAccountRef(accountId).flatMap(_.update(account => account.copy(balance = account.balance + amount)))
}

object Bank {
  def apply(): UIO[Bank] = for {
    map <- TMap.empty.commit
  } yield new Bank(map)
}