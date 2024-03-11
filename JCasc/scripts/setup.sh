#!/bin/bash
mv /tmp/plugins.txt /usr/share/jenkins/ref/plugins.txt
bash jenkins-plugin-cli -f /usr/share/jenkins/ref/plugins.txt
mv /tmp/init.groovy /usr/share/jenkins/ref/init.groovy.d/init.groovy
mkdir -p /usr/share/jenkins/ref/config
mv /tmp/*.yaml /usr/share/jenkins/ref/config/
