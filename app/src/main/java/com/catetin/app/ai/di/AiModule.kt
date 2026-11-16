package com.catetin.app.ai.di

import com.catetin.app.ai.engine.AiInferenceClient
import com.catetin.app.ai.engine.LocalLlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindLocalLlmEngine(impl: AiInferenceClient): LocalLlmEngine
}
