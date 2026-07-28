package dev.gomoku.yixindroid.data.review.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.yixindroid.data.game.GameFileIo
import dev.gomoku.yixindroid.data.review.ReviewRepositoryImpl
import dev.gomoku.yixindroid.domain.repository.GameFileReader
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
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
