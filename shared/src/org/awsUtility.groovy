package org.porvenir


def awsLogin(Map pipelineParams= [:]){
   
        withCredentials([[
        $class: 'AmazonWebServicesCredentialsBinding',
        credentialsId: pipelineParams.awscredentialid,
        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
        ]]) {
            script{
                env.AWS_ACCESS_KEY_ID = AWS_ACCESS_KEY_ID
                env.AWS_SECRET_ACCESS_KEY = AWS_SECRET_ACCESS_KEY
                env.AWS_REGION = pipelineParams.aws_region
                env.AWS_DEFAULT_OUTPUT = pipelineParams.aws_output
            
            }
        }
    
}


def accessTokenECR(Map pipelineParams) {
  ecr_token = sh (returnStdout: true, script: "aws ecr get-login-password --region ${pipelineParams.region}").trim()
    return ecr_token
}

def registryLoginImg(Map pipelineParams){
  wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: "${pipelineParams.tokenECR}", var: 'PSWD']]]) {
   sh "echo '${pipelineParams.tokenECR}'|img login -u AWS --password-stdin ${pipelineParams.registryName}"
  }  
}
