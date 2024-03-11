variables {
  imagebase = "jenkins/jenkins:2.401.1-lts-jdk11"
  imagename = "jenkins-devops"
  imageversion = "1.1-jenkins-2.401.1"
}

source "docker" "compile" {
  changes     = ["USER jenkins", "ENV JAVA_OPTS \"$${JAVA_OPTS} -Djava.awt.headless=true -Djenkins.install.runSetupWizard=false -Dmail.smtp.starttls.enable=false -Dmail.smtp.socketFactory.fallback \"", "ENV CASC_JENKINS_CONFIG $JENKINS_HOME/config", "ENV JENKINS_AUTHORIZATION_STRATEGY Matrix ", "ENTRYPOINT [\"/usr/bin/tini\", \"--\", \"/usr/local/bin/jenkins.sh\"]"]
  commit      = true
  image       = "${var.imagebase}"
  run_command = ["-d", "--name", "${var.imagename}", "-u", "root", "-i", "-t", "{{ .Image }}"]
}

build {
  sources = ["source.docker.compile"]

  provisioner "file" {
    destination = "/tmp/init.groovy"
    source      = "./scripts/init.groovy" 
  }
  provisioner "file" {
    destination = "/tmp/plugins.txt"
    source      = "./config/plugins.txt" 
  }
  

  provisioner "file" {
    destination = "/tmp/"
    source      = "./config/jcasc/"
  }

  provisioner "shell" {
    script= "./scripts/setup.sh"
    max_retries      = 2
  }

  post-processor "docker-tag" {
    repository = "azuepvgoydvpsptacr.azurecr.io/${var.imagename}"
    tags       = ["${var.imageversion}"]
  }
}
