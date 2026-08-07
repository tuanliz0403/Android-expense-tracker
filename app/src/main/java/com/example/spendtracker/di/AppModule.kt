package com.example.spendtracker.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.spendtracker.data.local.SpendTrackerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SpendTrackerDatabase =
        Room.databaseBuilder(context, SpendTrackerDatabase::class.java, "spend-tracker.db").build()

    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("spend_tracker_settings", Context.MODE_PRIVATE)
}
