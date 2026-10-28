package dev.gomoku.zenyth.data.rankings.di

import dev.gomoku.zenyth.data.rankings.RankingsRepositoryImpl
import dev.gomoku.zenyth.domain.repository.RankingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RankingsModule {

    @Binds
    @Singleton
    abstract fun bindRankingsRepository(impl: RankingsRepositoryImpl): RankingsRepository
}
