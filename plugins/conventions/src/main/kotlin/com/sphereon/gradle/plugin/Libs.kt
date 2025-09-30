package com.sphereon.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.sphereonlib
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("sphereonlib")

/*
val Project.androidLibs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("androidLibs")*/