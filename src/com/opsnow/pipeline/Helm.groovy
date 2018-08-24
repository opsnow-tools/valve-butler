#!/usr/bin/groovy
package com.opsnow;

def init() {
    println "+ helm init"
    sh "helm init"

    println "+ helm version"
    sh "helm version"

    println "+ helm repo list"
    sh "helm repo list"

    println "+ helm repo update"
    sh "helm repo update"

    println "+ helm plugin list"
    sh "helm plugin list"
}
