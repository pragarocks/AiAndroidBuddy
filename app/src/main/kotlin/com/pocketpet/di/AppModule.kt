package com.pocketpet.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.pocketpet.core.notifications.NotificationDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketpet_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideNotificationDatabase(@ApplicationContext context: Context): NotificationDatabase =
        Room.databaseBuilder(context, NotificationDatabase::class.java, "notifications.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideNotificationDao(db: NotificationDatabase) = db.notificationDao()
}
