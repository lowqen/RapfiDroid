package dev.gomoku.rapfidroid.data.review.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gomoku.rapfidroid.data.game.GameFileIo
import dev.gomoku.rapfidroid.data.review.ReviewRepositoryImpl
import dev.gomoku.rapfidroid.domain.repository.GameFileReader
import dev.gomoku.rapfidroid.domain.repository.ReviewRepository
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
