package dev.gomoku.zenyth.data.review.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.zenyth.data.game.GameFileIo
import dev.gomoku.zenyth.data.review.ReviewRepositoryImpl
import dev.gomoku.zenyth.domain.repository.GameFileReader
import dev.gomoku.zenyth.domain.repository.ReviewRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    abstract fun bindGameFileReader(impl: GameFileIo): GameFileReader
}
