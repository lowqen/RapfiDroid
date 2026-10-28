package dev.gomoku.zenyth.data.game.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.zenyth.data.game.BoardSessionStore
import dev.gomoku.zenyth.data.game.GameRepositoryImpl
import dev.gomoku.zenyth.data.game.PrefsBoardSessionStore
import dev.gomoku.zenyth.domain.repository.GameRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GameModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindBoardSessionStore(impl: PrefsBoardSessionStore): BoardSessionStore
}
