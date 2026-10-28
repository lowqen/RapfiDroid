package dev.gomoku.zenyth.data.prove.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.zenyth.data.prove.ProveRepositoryImpl
import dev.gomoku.zenyth.domain.repository.ProveRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProveModule {

    @Binds
    @Singleton
    abstract fun bindProveRepository(impl: ProveRepositoryImpl): ProveRepository
}
