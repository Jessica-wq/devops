import org.porvenir.scanUtility
import groovy.transform.Field

@Field def scan = new scanUtility()


/**********************************************************************************************************
*Metodo que permite realizar el scaneo de seguridad de las fuentes de los proyectos con FORTIFY  
*Paramatros:
    - routeScan: String, Ruta donde se encuentran las fuentes a analizar
***/
def call(Map pipelineParams){
    stage('Fortify'){
        scan.downloadFPR(pipelineParams.nameProject)
        def template = pipelineParams.template != null ? pipelineParams.template : '2021'
        if(pipelineParams.python == true){
            scan.scanFortifyPython(pipelineParams.routeScan,pipelineParams.version, pipelineParams.nameProject, template)
        }else if(pipelineParams.project == "app-mobile"){
            scan.scanFortifyMobile(pipelineParams.routeScan, pipelineParams.nameProject, template)
            println "se realizara cambio para "
        }else{
            scan.scanFortify(pipelineParams.routeScan, pipelineParams.nameProject, template)
            println "se realizara cambio para "
        }
        
        scan.uploadFPR(pipelineParams.nameProject)
        scan.verifyVulnerabilites(pipelineParams.nameProject, template)
    }
}
