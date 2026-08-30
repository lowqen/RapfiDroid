package dev.gomoku.rapfidroid.data.explorer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.rapfidroid.data.explorer.ExplorerRepositoryImpl
import dev.gomoku.rapfidroid.domain.repository.ExplorerRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExplorerModule {

    @Binds
    @Singleton
    abstract fun bindExplorerRepository(impl: ExplorerRepositoryImpl): ExplorerRepository
}
