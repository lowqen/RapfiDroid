package dev.gomoku.yixindroid.data.database.di

import dev.gomoku.yixindroid.data.database.DatabaseRepositoryImpl
import dev.gomoku.yixindroid.data.prefs.DbPrefsStore
import dev.gomoku.yixindroid.domain.repository.DatabaseRepository
import dev.gomoku.yixindroid.domain.repository.DatabaseSaver
import dev.gomoku.yixindroid.domain.repository.DbPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindDatabaseRepository(impl: DatabaseRepositoryImpl): DatabaseRepository

    @Binds
    @Singleton
    abstract fun bindDbPreferences(impl: DbPrefsStore): DbPreferences

    companion object {
        /** The prove run's view of the database: it only ever asks for a save. */
        @Provides
        @Singleton
        fun provideDatabaseSaver(database: DatabaseRepository): DatabaseSaver =
            DatabaseSaver { database.save() }
    }
}
