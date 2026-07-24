package dev.gomoku.yixindroid.data.rankings.di

import dev.gomoku.yixindroid.data.rankings.RankingsRepositoryImpl
import dev.gomoku.yixindroid.domain.repository.RankingsRepository
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
