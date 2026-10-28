package dev.gomoku.zenyth.data.explorer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.zenyth.data.explorer.ExplorerRepositoryImpl
import dev.gomoku.zenyth.domain.repository.ExplorerRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExplorerModule {

    @Binds
    @Singleton
    abstract fun bindExplorerRepository(impl: ExplorerRepositoryImpl): ExplorerRepository
}
