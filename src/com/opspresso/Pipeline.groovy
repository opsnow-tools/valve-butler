#!/usr/bin/groovy
package com.opspresso;

def helmInit() {
    println "+ helm init"
    sh "helm init"

    println "+ helm version"
    sh "helm version"
}
