#!/usr/bin/groovy
package com.opspresso;

def init() {
    println "+ helm version"
    sh "helm version"

    println "+ helm init"
    sh "helm init"

    println "+ helm repo list"
    sh "helm repo list"

    println "+ helm repo update"
    sh "helm repo update"

    println "+ helm plugin list"
    sh "helm plugin list"
}
