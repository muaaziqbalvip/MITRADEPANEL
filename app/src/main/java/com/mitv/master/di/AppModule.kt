package com.mitv.master.di

import com.mitv.master.data.repository.FirebaseRepository
import com.mitv.master.data.repository.UserTrackingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseRepository(): FirebaseRepository = FirebaseRepository()

    @Provides
    @Singleton
    fun provideUserTrackingRepository(): UserTrackingRepository = UserTrackingRepository()
}
