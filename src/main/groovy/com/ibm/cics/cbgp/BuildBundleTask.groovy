package com.ibm.cics.cbgp

/*-
 * #%L
 * CICS Bundle Gradle Plugin
 * %%
 * Copyright (C) 2019 IBM Corp.
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.file.copy.DefaultFileCopyDetails
import org.gradle.api.tasks.TaskAction

class BuildBundleTask extends DefaultTask {

    // Strings to share with test class
    public static final String CONFIG_NAME = "cicsBundle"
    public static final String MISSING_CONFIG = "Define \'$CONFIG_NAME\' configuration with CICS bundle dependencies"
    public static final String UNSUPPORTED_EXTENSIONS_FOUND = 'Unsupported file extensions for some dependencies, see earlier messages.'

    private static final List VALID_DEPENDENCY_FILE_EXTENSIONS = ['ear', 'jar', 'war']

    @TaskAction
    def buildCICSBundle() {
        logger.info "Task ${BundlePlugin.BUILD_TASK_NAME} (Gradle $project.gradle.gradleVersion) "

        // Find & process the configuration
        def foundConfig = false
        project.configurations.each {
            if (it.name == CONFIG_NAME) {
                processCICSBundle(it)
                foundConfig = true
            }
        }

        if (!foundConfig) {
            println()
            throw new GradleException(MISSING_CONFIG)
        }
    }

    def processCICSBundle(Configuration config) {
        logger.info "processing \'$CONFIG_NAME\' configuration"
        def filesCopied = []
        project.copy {
            from config
            eachFile {
                logger.lifecycle " Copying $it"
                filesCopied << it
            }
            into "$project.buildDir/$project.name-$project.version"
        }
        checkCopiedFileExtensions(filesCopied)
        checkDependenciesCopied(filesCopied, config)
    }

    private void checkDependenciesCopied(List filesCopied, Configuration config) {
        if (config.dependencies.size() == 0) {
            logger.warn "Warning, no external or project dependencies in 'cicsBundle' configuration"
            return
        }

        if (filesCopied.size() < config.dependencies.size()) {
            config.dependencies.each { dep ->
                def foundDependency = false
                for (def copied : filesCopied) {
                    if (copied instanceof DefaultFileCopyDetails) {
                        def copiedFullPath = copied.file.toString()
                        // Check here by dependency type
                        if (dep instanceof DefaultProjectDependency) {
                            if (copiedFullPath.contains(dep.dependencyProject.name)
                                    && copiedFullPath.contains(dep.dependencyProject.version)
                                    && copiedFullPath.contains(dep.dependencyProject.group)) {
                                foundDependency = true;
                                break;
                            }
                        } else if (dep instanceof DefaultExternalModuleDependency) {
                            if (copiedFullPath.contains(dep.name)
                                    && copiedFullPath.contains(dep.version)
                                    && copiedFullPath.contains(dep.group)) {
                                foundDependency = true;
                                break;
                            }
                        } else throw new GradleException("Unexpected dependency type" + dep.class.toString() + "for dependency $dep")
                    }
                }
                if (!foundDependency) {
                    logger.error " Missing dependency: $dep"
                }
            }
            throw new GradleException("Failed, missing dependencies from '$CONFIG_NAME' configuration")
        }
    }

    private void checkCopiedFileExtensions(List filesCopied) {
        def allExtensionsOk = true
        filesCopied.each() {
            def name = it.name
            def splits = name.split('\\.')
            def extension = splits[splits.length - 1]
            def extensionOK = (splits.size() >= 2 && VALID_DEPENDENCY_FILE_EXTENSIONS.contains(extension))
            if (!extensionOK) {
                logger.error "Unsupported file extension '$extension' for copied dependency '$it.path'"
                allExtensionsOk = false
            }
        }
        if (!allExtensionsOk) {
            throw new GradleException(UNSUPPORTED_EXTENSIONS_FOUND)
        }

    }

}