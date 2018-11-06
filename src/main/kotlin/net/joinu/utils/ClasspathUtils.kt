package net.joinu.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.IOException


object ClasspathUtils {

    @Throws(ClassNotFoundException::class, IOException::class)
    suspend fun getClassesOfPackage(packageNames: List<String>): List<Class<*>> {
        val classLoader = ClassLoader.getSystemClassLoader()

        return coroutineScope {
            packageNames
                .map { packageName ->
                    classLoader
                        .getResources(packageName.replace('.', '/'))
                        .toList()
                        .map { File(it.file) }
                        .map { async { findClasses(it, packageName) } }
                        .awaitAll()
                        .flatten()
                }
                .flatten()
        }
    }

    @Throws(ClassNotFoundException::class)
    private suspend fun findClasses(directory: File, packageName: String): List<Class<*>> {
        if (!directory.exists()) {
            return emptyList()
        }

        val files = directory.listFiles()

        val containedClasses = coroutineScope { files
            .asSequence()
            .filter { it.isDirectory && !it.name.contains(".") }
            .map { async { findClasses(it, "$packageName.${it.name}") } }
            .toList()
            .awaitAll()
            .flatten()
        }

        val classes = files
            .filter { it.name.endsWith(".class") }
            .map { Class.forName("$packageName.${it.name.substring(0, it.name.length - 6)}") }

        return containedClasses + classes
    }
}