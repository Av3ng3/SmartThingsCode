 /*  
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
definition(
    name: "keenectZone",
    namespace: "BrianMurphy",
    author: "Mike Maxwell & Brian Murphy",
    description: "zone application for 'Keenect', do not install directly.",
    category: "My Apps",
    parent: "BrianMurphy:Keenect",
    iconUrl: "https://raw.githubusercontent.com/MikeMaxwell/smartthings/master/keen-app-icon.png",
    iconX2Url: "https://raw.githubusercontent.com/MikeMaxwell/smartthings/master/keen-app-icon.png",

)

preferences {
	page(name: "main")
    page(name: "advanced")
}

def installed() {
	log.debug "Installed with settings: ${settings}"
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	state.vChild = "2.0"
    unsubscribe()
	initialize()
}

def initialize() {
	state.vChild = "2.0"
    parent.updateVer(state.vChild)
    subscribe(tempSensors, "temperature", tempHandler)
    subscribe(vents, "level", levelHandler)
    subscribe(zoneControlSwitch,"switch",zoneDisableHandeler)
    //subscribe(zoneindicateoffsetSwitch,"switch",allzoneoffset)
	//subscribe(zoneneedoffsetSwitch,"switch",allzoneoffset)
    state.isAC = parent.isAC() //AC enable bits
   	fetchZoneControlState()
    zoneEvaluate(parent.notifyZone())
}

//dynamic page methods
def main(){
	//state.etf = parent.getID()
	def installed = app.installationState == "COMPLETE"
    state.outputreduction =false
	return dynamicPage(
    	name		: "main"
        ,title		: "Zone Configuration"
        ,install	: true
        ,uninstall	: installed
        ){	section(){
          		label(
                   	title		: "Name the zone"
                    ,required	: true
                )      
        	}
		    section("Devices"){
                /*
				only stock device types work in the list below???
                ticket submitted, as this should work, and seems to work for everyone except me...
				*/
                input(
                    name			: "vents"
                    ,title			: "Keen vents in this Zone:"
                    ,multiple		: true
                    ,required		: true
                    //,type			: "device.KeenHomeSmartVent"
                    ,type			: "capability.switchLevel"
                    ,submitOnChange	: true
				)
 				input(
            		name		: "tempSensors"
                	,title		: "Temperature Sensor:"
                	,multiple	: false
                	,required	: true
                	,type		: "capability.temperatureMeasurement"
                    ,submitOnChange	: false
            	) 
            }
            section("Settings"){
				input(
            		name			: "minVo"
                	,title			: "Minimum vent opening"
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options		: minVoptions()
                    ,submitOnChange	: true
            	) 
                if (minVo){
					input(
            			name			: "maxVo"
                		,title			: "Maximum vent opening"
                		,multiple		: false
                		,required		: true
                		,type			: "enum"
                    	,options		: maxVoptions()
                    	,defaultValue	: "100"
                    	,submitOnChange	: true
            		) 
                }
                input(
                	name			: "zoneControlType"
                    ,title			: "Temperature control type"
                    ,multiple		: false
                    ,required		: true
                    ,type			: "enum"
                    ,options		: [["offset":"Offset from Main set point"],["fixed":"Fixed"]]
                    ,defaultValue	: "offset"
                    ,submitOnChange	: true
                )
                    if (parent.isAC()){
                    input(
                        name            : "minVoC"
                        ,title          : "Optional minimum vent opening for cooling"
                        ,multiple       : false
                        ,required       : false
                        ,type           : "enum"
                        ,options        : minVoptions()
                        ,submitOnChange : true
                    ) 
                    if (minVoC) {
                        input(
                            name            : "maxVoC"
                            ,title          : "Optional maximum vent opening for cooling"
                            ,multiple       : false
                            ,required       : false
                            ,type           : "enum"
                            ,options        : maxVCoptions()
                            //,defaultValue : "100"
                            ,submitOnChange : true
                        ) 
                    }
                }
                if (zoneControlType == "offset"){
					input(
            			name			: "heatOffset"
                		,title			: "Heating offset, (above or below main thermostat)"
                		,multiple		: false
                		,required		: false
                		,type			: "enum"
                    	,options 		: zoneTempOptions()
                    	,defaultValue	: "0"
                    	,submitOnChange	: false
            		) 
                    if (parent.isAC()){
						input(
            				name			: "coolOffset"
                			,title			: "Cooling offset, (above or below main thermostat)"
                			,multiple		: false
                			,required		: false
                			,type			: "enum"
                    		,options 		: zoneTempOptions()
                    		,defaultValue	: "0"
                    		,submitOnChange	: false
            			)
                     }
                } else {
                	input(
            			name			: "staticHSP"
                		,title			: "Heating set point"
                		,multiple		: false
                		,required		: false
                		,type			: "enum"
                    	,options 		: zoneFixedOptions()
                    	,defaultValue	: "70"
                    	,submitOnChange	: false
            		) 
                    if (parent.isAC()){
                    	input(
            				name			: "staticCSP"
                			,title			: "Cooling set point"
                			,multiple		: false
                			,required		: false
                			,type			: "enum"
                    		,options 		: zoneFixedOptions()
                    		,defaultValue	: "70"
                    		,submitOnChange	: false
                        )
                    }
                }
            }
            section("Advanced"){
				def afDesc = "\t" + getTitle("AggressiveTempVentCurve") + "\n\t" + getTitle("ventCloseWait") + "\n\t" + getTitle("zoneControlSwitchSummary") + "\n\t" + getTitle("logLevelSummary") + "\n\t" + getTitle("sendEventsToNotificationsSummary") + "\n\t" + getTitle("pressureControl")
                href( "advanced"
                    ,title			: ""
					,description	: afDesc
					,state			: null
				)
            }
	}
}

def advanced(){
	state?.integrator =0
    def pEnabled = false
    try{ pEnabled = parent.hasPressure() }
    catch(e){}
    return dynamicPage(
    	name		: "advanced"
        ,title		: "Advanced Options"
        ,install	: false
        ,uninstall	: false
        ){
         section(){
          		input(
            		name			: "AggressiveTempVentCurve"
                	,title			: getTitle("AggressiveTempVentCurve") 
                	,multiple		: false
                	,required		: false
                	,type			: "bool"
                    ,submitOnChange	: true
                    ,defaultValue	: false
            	)          
            	input(
            		name			: "ventCloseWait"
                    ,title			: getTitle("ventCloseWait")
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                	,options		: [["-2":"On disable only"],["-1":"Do not close"],["0":"Immediate"],["60":"After 1 Minute"],["120":"After 2 Minutes"],["180":"After 3 Minutes"],["240":"After 4 Minutes"],["300":"After 5 Minutes"]]
                	,submitOnChange	: true
                   	,defaultValue	: "-1"
            	)
                input(
            		name			: "zoneControlSwitch"
                	,title			: getTitle("zoneControlSwitch") 
                	,multiple		: false
                	,required		: false
                	,type			: "capability.switch"
                    ,submitOnChange	: true
                )
               /*input(
            		name			: "zoneindicateoffsetSwitch"
                	,title			: getTitle("zoneindicateoffsetSwitch") 
                	,multiple		: false
                	,required		: false
                	,type			: "capability.switch"
                    ,submitOnChange	: true
            	) 
                    input(
            		name			: "zoneneedoffsetSwitch"
                	,title			: getTitle("zoneneedoffsetSwitch") 
                	,multiple		: false
                	,required		: false
                	,type			: "capability.switch"
                    ,submitOnChange	: true
            	)*/      
         		input(
            		name			: "logLevel"
                	,title			: "IDE logging level" 
                	,multiple		: false
                	,required		: true
                	,type			: "enum"
                    ,options		: getLogLevels()
                	,submitOnChange	: false
                   	,defaultValue	: "10"
            	)            
          		input(
            		name			: "sendEventsToNotifications"
                	,title			: getTitle("sendEventsToNotifications") 
                	,multiple		: false
                	,required		: false
                	,type			: "bool"
                    ,submitOnChange	: true
                    ,defaultValue	: false
            	)      
                if (pEnabled){
          			input(
            			name			: "pressureControl"
                		,title			: getTitle("pressureControl") 
                		,multiple		: false
                		,required		: false
                		,type			: "bool"
                    	,submitOnChange	: true
                    	,defaultValue	: true
            		)                  
				}              
        }
    }
}

//zone control methods
def zoneEvaluate(params){
	logger(40,"debug","zoneEvaluate:enter-- parameters: ${params}")
	
    // variables
    def evaluateVents = false
    
    def msg = params.msg
    def data = params.data
    //main states
    
    def mainStateLocal = state.mainState ?: ""
    def mainModeLocal = state.mainMode ?: ""
	def mainHSPLocal = state.mainHSP  ?: 0
	def mainCSPLocal = state.mainCSP  ?: 0
	def mainOnLocal = state.mainOn  ?: ""

	//zone states    
	def zoneDisabledLocal = fetchZoneControlState()
    def runningLocal
    
    //always fetch these since the zone ownes them
    def zoneTempLocal = tempSensors.currentValue("temperature").toFloat()
    def coolOffsetLocal 
    if (settings.coolOffset) coolOffsetLocal = settings.coolOffset.toInteger()
    def heatOffsetLocal = settings.heatOffset.toInteger()
    def zoneCloseOption = -1
    if (settings.ventCloseWait) zoneCloseOption = settings.ventCloseWait.toInteger()
    
    def minVoLocal = settings.minVo.toInteger() 
    def maxVoLocal = settings.maxVo.toInteger()
     def minVoCLocal = settings.minVoC.toInteger() 
    def maxVoCLocal = settings.maxVoC.toInteger()
    
    
    def VoLocal = state.zoneVoLocal
    
    def pEnabled = false
    
    try{ pEnabled = parent.hasPressure() }
    catch(e){} 
    if (pEnabled && settings.pressureControl != false){
    	//back off adjustment here
        def backOff = parent.getBackoff()
        if (backOff > 0){
            logger(10,"warn","Keenect says backoff vents by: ${backOff}%")
        	if (minVoLocal != 100 && (minVoLocal + backOff) < 100){
                logger(20,"info","zoneEvaluate- backOff minVo- current settings: ${minVoLocal}, changed to: ${minVoLocal + backOff}")
        		minVoLocal = minVoLocal + backOff
        	} else {
				logger(20,"info","zoneEvaluate- backOff could not change minVo (it's already at 100%)")
			}
        	if (maxVoLocal != 100 && (maxVoLocal + backOff) < 100){
                logger(20,"info","zoneEvaluate- backOff maxVo- current settings: ${maxVoLocal}, changed to: ${maxVoLocal + backOff}")
        		maxVoLocal = maxVoLocal + backOff
        	} else {
                logger(20,"info","zoneEvaluate- backOff could not change maxVo (it's already at 100%)")
        	}
        }
    }
   
    //set it here depending on zoneControlType
    def zoneCSPLocal = mainCSPLocal + coolOffsetLocal
    if (mainCSPLocal && coolOffsetLocal) zoneCSPLocal = (mainCSPLocal + coolOffsetLocal)
    def zoneHSPLocal = mainHSPLocal + heatOffsetLocal
    if (settings.zoneControlType == "fixed"){
    	if (mainCSPLocal && settings.staticCSP)	zoneCSPLocal = settings.staticCSP.toInteger()
        zoneHSPLocal = settings.staticHSP.toInteger()
    }
    
    switch (msg){
    	case "stat" :
                //initial request for info during app install and zone update
                if (data.initRequest){
                	if (!zoneDisabledLocal) evaluateVents = data.mainOn
                //set point changes, ignore setbacks
                } else if (data.mainOn && (mainHSPLocal < data.mainHSP || mainCSPLocal > data.mainCSP) && !zoneDisabledLocal) {
                    evaluateVents = true
                    logger(30,"info","zoneEvaluate- set point changes, evaluate: ${true}")
                //system state changed
                } else if (data.mainStateChange){
                	//system start up
                	if (data.mainOn && !zoneDisabledLocal){
                        evaluateVents = true
                        logger(30,"info","zoneEvaluate- system start up, evaluate: ${evaluateVents}")
                        logger(10,"info","Main HVAC is on and ${data.mainState}ing")
                    //system shut down
                    } else if (!data.mainOn && !zoneDisabledLocal){
                    	runningLocal = false
                        def asp = state.activeSetPoint
                        def d
                        if (zoneTempLocal != null && asp != null){
                            d = (zoneTempLocal - asp).toFloat()
                            d = d.round(1)
                        }
                         state.integrator=0 // temp remove later
                    	log.info "integrator ${d}"
                       
                        state?.integrator = (state.integrator + (d))
                        if (state.integrator >= 4) {
                        state.integrator =4
                        logger(20,"info", "state.integrator truncated to 4")
                        }
                         if (state.integrator <= -4) {
                        state.integrator =-4
                        logger(20,"info", "state.integrator truncated to -4")
                        }
                        
                        log.info "state.integrator ${state.integrator}"
                        if (state.AggressiveTempVentCurveActive) {
                        state?.integrator = 0
                        log.info "state.integrator QR ${state.integrator}"}
                        state.endReport = "\n\tsetpoint: ${tempStr(asp)}\n\tend temp: ${tempStr(zoneTempLocal)}\n\tvariance: ${tempStr(d)}\n\tvent levels: ${vents.currentValue("level")}%"        
                        logger(10,"info","Main HVAC has shut down.")                        
                        state.acactive = false
						//check zone vent close options from zone
                    	if (zoneCloseOption >= 0){
                        	 closeWithOptions(zoneCloseOption)
                       	} 
     				}
                } else {
                	logger(30,"warn","zoneEvaluate- ${msg}, no matching events")
                }
                
                //always update data
                
                mainStateLocal = data.mainState
                mainModeLocal = data.mainMode
                mainHSPLocal = data.mainHSP
                mainCSPLocal = data.mainCSP
                mainOnLocal = data.mainOn
                //set it again here, or rather ignore if type is fixed...
                if (zoneControlType == "offset"){
              zoneCSPLocal =  (mainCSPLocal + coolOffsetLocal)
                zoneHSPLocal = (mainHSPLocal + heatOffsetLocal)
                }
        	break
        case "temp" :
                if (!zoneDisabledLocal){
                	logger(30,"debug","zoneEvaluate- zone temperature changed, zoneTemp: ${zoneTempLocal}")
                	evaluateVents = true
                } else {
                    logger(30,"warn", "Zone temp change ignored, zone is disabled")
                }
        	break
        case "vent" :
        		logger(30,"debug","zoneEvaluate- msg: ${msg}, data: ${data}")
        	break
        case "zoneSwitch" :
                //fire up zone since it was activated
                if (!zoneDisabledLocal){
                	evaluateVents = true
                //shut it down with options
                } else {
                	if (mainOnLocal){
                  		def asp = state.activeSetPoint
                        def d
                        if (zoneTempLocal != null && asp != null){
                            d = (zoneTempLocal - asp).toFloat()
                            d = d.round(1)
                        }
                        state.endReport = "\n\tsetpoint: ${tempStr(asp)}\n\tend temp: ${tempStr(zoneTempLocal)}\n\tvariance: ${tempStr(d)}\n\tvent levels: ${vents.currentValue("level")}%"                    
						runningLocal = false
    				} 
                    //check zone vent close options from zone
                    if (zoneCloseOption == -2){
                    	closeWithOptions(0)
                    }
                    logger(10,"info", "Zone was disabled, we won't be doing anything alse until it's re-enabled")
                }
        	break
        case "pressure" :
        		logger(30,"debug","zoneEvaluate- msg: ${msg}, data: ${data}")
        	break
        case "pressureAlert" :
               	logger(30,"debug","zoneEvaluate- pressureAlert, data: ${data}")
                //notifyZones([msg:"pressureAlert",data:state.voBackoff])
                logger(10,"warn","Pressure alert cleared, resetting zone VO's...")
				//if pressure is disabled locally, reset vents to previous vo
                if (settings.pressureControl == false){
                	if (state.lastVO != null){
                    	setVents(state.lastVO)
                    }
                } else {
                	evaluateVents = true
                }
        	break
    }    
    
    //always check for main quick  AggressiveTempVentCurveActive
   // use this area to boost tegan and ethan heat multiplier at night mode
   
   def tempBool = false
   state.AggressiveTempVentCurveActive = false
    if (settings.AggressiveTempVentCurve){
    	 state.AggressiveTempVentCurveActive = true
            }
           
    	
   
    
    //write state
    state.mainState = mainStateLocal
    state.mainMode = mainModeLocal
	state.mainHSP = mainHSPLocal
	state.mainCSP = mainCSPLocal
	state.mainOn = mainOnLocal
    state.zoneCSP = zoneCSPLocal
    state.zoneHSP = zoneHSPLocal
    state.zoneTemp = zoneTempLocal
	state.zoneDisabled = zoneDisabledLocal
    
  
    if (evaluateVents){
    def outred = false  
    state.integrator=0 // temp
    	def slResult = ""
       	if (mainStateLocal == "heat"){
        state.acactive = true
  log.info "CHILD evaluateVents Heat"
 state.zoneneedofset = false

        	state.activeSetPoint = zoneHSPLocal
            if (zoneTempLocal < zoneHSPLocal-1.5){
            state.zoneneedofset = true
            logger(10,"info","CHILD zone needs offset")
            }
            if (zoneTempLocal > zoneHSPLocal-1.5) {
             state.zoneneedofset = false
             logger(10,"info","CHILD zone dose not need offset")
             }
            
       		if (zoneTempLocal >= zoneHSPLocal){
            	state.lastVO = minVoLocal
           		slResult = setVents(minVoLocal)
                if (state.outputreduction){
                slResult = setVents(0)
                }
             	logger(10,"info","CHILD Zone temp is ${tempStr(zoneTempLocal)}, heating setpoint of ${tempStr(zoneHSPLocal)} is met${slResult}")
				runningLocal = false
          	} else {
           			
                     if (state.AggressiveTempVentCurveActive){
                     
                     VoLocal=Math.round(((zoneHSPLocal - zoneTempLocal) + (1.0))*66)
                       logger(20,"info","MM >3 QR Vent request level ${VoLocal}")
                     if (VoLocal<=40){
          		  VoLocal=40
                   logger(20,"info","QR active <=40")}
                     }
                     else {VoLocal=Math.round(((zoneHSPLocal - zoneTempLocal)+(0.75))*40)
                     }
                 
                   if (VoLocal>100){
           					 VoLocal=100}
                         if (VoLocal< 0){
          		 			 VoLocal = 0}
                  
            		                        //   log.info "output reduction ${state.outputreduction} zone need ${state.zoneneedofset}"

                 if (state.outputreduction){
                 if (state.zoneneedofset){
                 
               logger(30,"info","this zone < than 1.5 degree from setpoint output not reduced ${VoLocal}")
                  }else {
                  log.info"output prior to reduction for this zone ${VoLocal}"
                 
                 outred = true
                 VoLocal=VoLocal*0.30
                  log.info"output reduction for this zone needed ${VoLocal}"
                  }
                  }
                   	
                    
                    if (VoLocal >= maxVoLocal){
                        VoLocal = maxVoLocal}
                        
                        if (outred == false){
                        if (VoLocal <= minVoLocal){
                        VoLocal = minVoLocal
                        log.info"vent at min VoLocal ${VoLocal}"}
                        }
                        
                  if (VoLocal>100){
           					 VoLocal=100}
                         if (VoLocal< 0){
          		 			 VoLocal = 0}
            		slResult = setVents(VoLocal) 
                  
                  
                  
                  
					logger(10,"info", "Child zone temp is ${tempStr(zoneTempLocal)}, heating setpoint of ${tempStr(zoneHSPLocal)} is not met${slResult} output reduction ${outred}")
					runningLocal = true
            }   
           	
        } else if (mainStateLocal == "cool"){
                   	   log.info "CHILD evaluateVents Cooling"

        	state.activeSetPoint = zoneCSPLocal
                    state.zoneneedofset = false
                    state.acactive = true
            if (zoneTempLocal > zoneCSPLocal+1.5){
         state.zoneneedofset = true
           logger(10,"info","CHILD zone needs offset")
            }
            if (zoneTempLocal < zoneCSPLocal+1.5) {
             state.zoneneedofset = false
            logger(10,"info", "CHILD zone dose not need offset")
             }
  
       		if (zoneTempLocal <= zoneCSPLocal){
            	state.lastVO = minVoCLocal
         	 	VoLocal = minVoCLocal     
           		slResult = setVents(VoLocal)
                 
             	logger(10,"info", "CHILD zone temp is ${tempStr(zoneTempLocal)}, cooling setpoint of ${tempStr(zoneCSPLocal)} is met${slResult}")
				runningLocal = false
          	} else {
           			 
                     if (state.AggressiveTempVentCurveActive) 
                     {VoLocal=Math.round(((zoneTempLocal - zoneCSPLocal)+ 0.2 )*300)
                       logger(30,"info","MM >3 QR Vent request level ${VoLocal}")
                     if (VoLocal<=40){
          		  VoLocal=40
                    logger(30,"info","QR active <=40")}
                     }
                                         // else {VoLocal=Math.round(((zoneTempLocal - zoneCSPLocal) - (state.integrator))*140)


                     else {VoLocal=Math.round(((zoneTempLocal - zoneCSPLocal)+0.2)*150)
                     logger(30,"info","SM >3 Saved state.integrator/2 ${state.integrator}")
                     }
                   
                 
                                                //          log.info "output reduction ${state.outputreduction} zone need ${state.zoneneedofset}"
      if (VoLocal>100){
           					 VoLocal=100}
                         if (VoLocal< 0){
          		 			 VoLocal = 0}
                             
            if (state.outputreduction){
                 if (state.zoneneedofset){
               logger(30,"info","this zone > than 1.5 degree from setpoint output not reduced ${VoLocal}")
                  }else {
                  log.info"output prior to reduction for this zone ${VoLocal}"
                 
                 outred = true
                 VoLocal=VoLocal*0.30
                  log.info"output reduction for this zone needed ${VoLocal}"
                  }
                  }
                   	
                    
                    if (VoLocal >= maxVoCLocal){
                        VoLocal = maxVoCLocal}
                        
                        if (outred == false){
                        if (VoLocal <= minVoCLocal){
                        VoLocal = minVoCLocal
                        log.info"vent at min VoLocal ${VoLocal}"}
                        }
                        
                  if (VoLocal>100){
           					 VoLocal=100}
                         if (VoLocal< 0){
          		 			 VoLocal = 0}
            		slResult = setVents(VoLocal)
					logger(10,"info", "CHILD zone temp is ${tempStr(zoneTempLocal)}, cooling setpoint of ${tempStr(zoneCSPLocal)} is not met${slResult} output reduction ${outred}")
					runningLocal = true
            }   
            
        }else if (mainStateLocal == "fan only"){
        
             if (state.acactive != true) {
             VoLocal = 100
            logger(10,"info"," fan on open vents to 100, mainState: ${mainStateLocal}, zoneTemp: ${zoneTempLocal}, zoneHSP: ${zoneHSPLocal}, zoneCSP: ${zoneCSPLocal}")
            }
            if (state.acactive == true) {
                if (VoLocal <40){
                VoLocal = 40}
            logger(10,"info","fan on after heat or AC nothing to do, mainState: ${mainStateLocal}, zoneTemp: ${zoneTempLocal}, zoneHSP: ${zoneHSPLocal}, zoneCSP: ${zoneCSPLocal}")
            } 
        setVents(VoLocal)
        } else {
            logger(10,"info","Nothing to do, main HVAC is not running, mainState: ${mainStateLocal}, zoneTemp: ${zoneTempLocal}, zoneHSP: ${zoneHSPLocal}, zoneCSP: ${zoneCSPLocal}")
       	}
                                                                //  log.info "output reduction ${state.outputreduction} zone need ${state.zoneneedofset}"

      if (state.zoneneedofset == false){
parent.manageoutputreduction(false)
 log.info "CHILD Clearing System Reduced Ouput"
             }
      if (state.zoneneedofset == true){

          parent.manageoutputreduction(true)
 log.info "CHILD Requesting System Reduced Ouput"
             }      
        
        
    }
    //write state
         
             
 	state.running = runningLocal
state.zoneVoLocal =  VoLocal
    logger(40,"debug","zoneEvaluate:exit- ")
}


//event handlers
def obstructionHandler(evt){
    if (evt.value == "obstructed"){
        def vent = vents.find{it.id == evt.deviceId}
        logger(10,"warn", "Attempting to clear vent obstruction on: [${vent.displayName}]")
        vent.clearObstruction()
    }
 
    /*
      name: "switch",
      value: "obstructed",
      call: device.clearObstruction
    */
}
def levelHandler(evt){
	logger(40,"debug","levelHandler:enter- ")
    def ventData = state."${evt.deviceId}"
    def v = evt.value.toFloat().round(0).toInteger()
    def t = evt.date.getTime()
    if (ventData){
        //request
        if (evt.description == ""){
			ventData.voRequest = v	
            ventData.voRequestTS = t
            logger(30,"debug","levelHandler- request vo: ${v} t: ${t}")
		//response
		} else {
        	ventData.voResponse = v
            ventData.voResponseTS = t
            ventData.voTTC = ((t - ventData.voRequestTS) / 1000).toFloat().round(1)
            logger(30,"debug","levelHandler- response vo: ${v} t: ${t} voTTC: ${ventData.voTTC}")
        }
    } else {
    	//request
    	if (evt.description == ""){
    		state."${evt.deviceId}" =  [voRequest:v,voRequestTS:t,voResponse:null,voResponseTS:null,voTTC:null] 
            logger(30,"debug","levelHandler-init request vo: ${v} t: ${t}")
        //response
        } else {
        	state."${evt.deviceId}" =  [voRequest:null,voRequestTS:null,voResponse:t,voResponseTS:v,voTTC:null] 
            logger(30,"debug","levelHandler-init response vo: ${v} t: ${t}")
        }
    }
    
    logger(40,"debug","levelHandler:exit- ")
}

def zoneDisableHandeler(evt){
    logger(40,"debug","zoneDisableHandeler- evt name: ${evt.name}, value: ${evt.value}")
    def zoneIsEnabled = evt.value == "on"
    if (zoneControlSwitch){
    	if (zoneIsEnabled){
       		logger(10,"warn", "Zone was enabled via: [${zoneControlSwitch.displayName}]")
    	} else {
       		logger(10,"warn", "Zone was disabled via: [${zoneControlSwitch.displayName}]")
    	}
    	zoneEvaluate([msg:"zoneSwitch"])
    }
    logger(40,"debug","zoneDisableHandeler:exit- ")
}

def allzoneoffset(val){
  //  logger(10,"info","From Parent Output reduction value: ${val}")
    if (val == true){
    	
       		logger(30,"info", "Zone output reduction enabled")
            state.outputreduction = true
            
    	} else if (val == false){
       		logger(30,"info", "Zone output redution disabled")
            state.outputreduction =false
    	}
   // logger(10,"info","zone output reduction:exit- ")
}


def tempHandler(evt){
    logger(40,"debug","tempHandler- evt name: ${evt.name}, value: ${evt.value}")
    state.zoneTemp = evt.value.toFloat()
    if (state.mainOn){
    	logger(30,"debug","tempHandler- tempChange, value: ${evt.value}")
    	zoneEvaluate([msg:"temp", data:["tempChange"]])	
    }     
}

//misc utility methods
def closeWithOptions(zoneCloseOption){
	if (zoneCloseOption == 0){
		logger(10,"warn", "Vents closed via close vents option")
		setVents(0)
	} else if (zoneCloseOption > 0){
		logger(10,"warn", "Vent closing is scheduled in ${zoneCloseOption} seconds")
		runIn(zoneCloseOption,delayClose)
	}          	
}

def fetchZoneControlState(){
	logger(40,"debug","fetchZoneControlState:enter- ")
   if (zoneControlSwitch){
    	state.zoneDisabled = zoneControlSwitch.currentValue("switch") == "off"
     	logger (30,"info","A zone control switch is selected and zoneDisabled is: ${state.zoneDisabled}")
    } else {
    	state.zoneDisabled = false
        logger (30,"info","A zone control switch is not selected and zoneDisabled is: ${state.zoneDisabled}")
    }
    logger(40,"debug","fetchZoneControlState:exit- ")
    return state.zoneDisabled
}

def logger(displayLevel,errorLevel,text){
	//input logLevel 1,2,3,4,-1
    /*
    [1:"Lite"],[2:"Moderate"],[3:"Detailed"],[4:"Super nerdy"]
    input 	logLevel
    
    1		Lite		
    2		Moderate	
    3		Detailed
    4		Super nerdy
    
    errorLevel 	color		number
    error		red			5
    warn		yellow		4
    info		lt blue		3
    debug		dk blue		2
    trace		gray		1
    */
    def logL = 10
    if (logLevel) logL = logLevel.toInteger()
    
    if (logL == 0) {return}//bail
    else if (logL >= displayLevel){
    	log."${errorLevel}"(text)
        if (sendEventsToNotifications && displayLevel == 10){
        	def nixt = now() + location.timeZone.rawOffset
        	def today = new Date(nixt).format("HH:mm:ss.Ms")
        	text = today + ": " + text
        	sendNotificationEvent(app.label + ": " + text) 
        }
    }
 }

def setVents(newVo){
	logger(40,"debug","setVents:enter- ")
	logger(30,"warn","setVents- newVo: ${newVo}")
    def result = ""
    def changeRequired = false
    
	settings.vents.each{ vent ->
    	def changeMe = false
		def crntVo = vent.currentValue("level").toInteger()
        def isOff = vent.currentValue("switch") == "off"
        /*
        	0 = 0 for sure
        	> 90 = 100, usually
        	the remainder is a crap shoot
            0 == switch == "off"
            > 0 == switch == "on"
            establish an arbitrary +/- threshold
            if currentLevel is +/- 5 of requested level, call it good
            otherwise reset it
		*/
        if (newVo != crntVo){
        	def lB = crntVo - 5
            def uB = crntVo + 5
        	if (newVo == 100 && crntVo < 97){
            	//logger(10,"info","newVo == 100 && crntVo < 90: ${newVo == 100 && crntVo < 90}")
            	changeMe = true
            } else if ((newVo < lB || newVo > uB) && newVo != 100){
            	//logger(10,"info","newVo < lB || newVo > uB && newVo != 100: ${(newVo < lB || newVo > uB) && newVo != 100}")
            	changeMe = true
            }
        }
        if (changeMe || isOff){
        	changeRequired = true
        	vent.setLevel(newVo)
        }
        logger(30,"info","setVents- [${vent.displayName}], changeRequired: ${changeMe}, new vo: ${newVo}, current vo: ${crntVo}")
    }
    def mqText = ""
    if (state.mainQuick && settings.AggressiveTempVentCurve && newVo == 100){
    	mqText = ", quick recovery active"
    }
    if (changeRequired) result = ", setting vents to ${newVo}%${mqText}"
    else result = ", vents at ${newVo}%${mqText}"
 	return result
    logger(40,"debug","setVents:exit- ")
}

def delayClose(){
    setVents(0)
    logger(10,"warn","Vent close executed")
}

def tempStr(temp){
    def tc = state.tempScale ?: location.temperatureScale
    if (temp) return "${temp.toString()}°${tc}"
    else return "No data available yet."
}

//dynamic page helpers
def getTitle(name){
	def title = ""
	switch(name){
    	case "AggressiveTempVentCurve" :
        	title = settings.AggressiveTempVentCurve ?  "Aggressive Temp vent Curve is [on]" : "Aggressive Temp vent Curve is  [off]"
        	break
        case "ventCloseWait" :
        	title = 'Close vent options are '
            if (!settings.ventCloseWait || settings.ventCloseWait == "-1"){
               	title = title + "[off]"
            } else {
             	title = title + "[on]"
            }
        	break
        case "zoneControlSwitch" :
        	title = settings.zoneControlSwitch ? "Optional zone control switch:\n\twhen on, zone is enabled\n\twhen off, zone is disabled " : "Optional zone control switch"
        	break
        case "zoneControlSwitchSummary" :
        	title = settings.zoneControlSwitch ? "Zone control switch: selected" : "Zone control switch: not selected"
        	break            
              /*case "zoneindicateoffsetSwitch" :
        	title = settings.zoneindicateoffsetSwitch ? "Master zone offset switch: selected" : "Master zone offset switch: not selected"
        	break  
              case "zoneneedoffsetSwitch" :
        	title = settings.zoneneedoffsetSwitch ? "Zone request offset switch: selected" : "Zone request offset switch: not selected"
        	break */ 
            
            
        case "logLevelSummary" :
        	title = "Log level is " + getLogLevel(settings.logLevel)
        	break            
        case "sendEventsToNotifications" :
        	title = settings.sendEventsToNotifications ?  "Send Lite events to notification feed is [on]" : "Send Lite events to notification feed is [off]" 
        	break   
        case "sendEventsToNotificationsSummary" :
        	title = settings.sendEventsToNotifications ?  "Notification feed is [on]" : "Notification feed is [off]" 
        	break   
		case "pressureControl" :
        	def pEnabled = false
            try{ pEnabled = parent.hasPressure() }
    		catch(e){}
        	if (pEnabled){
            	if (settings.pressureControl == false){
            		title = "Pressure management is [off]" 
            	} else {
            		title = "Pressure management is [on]"
            	}
            }
        	break               
	}
    return title
}

def minVoptions(){
	return [["0":"Fully closed"],["5":"5%"],["10":"10%"],["15":"15%"],["20":"20%"],["25":"25%"],["30":"30%"],["35":"35%"],["40":"40%"],["45":"45%"],["50":"50%"],["55":"55%"],["60":"60%"]]
}
def maxVCoptions(){
    def opts = []
    def start = minVoC.toInteger() + 5
    start.step 95, 5, {
        opts.push(["${it}":"${it}%"])
    }
    opts.push(["100":"Fully open"])
    return opts
}
def maxVoptions(){
	def opts = []
    def start = minVo.toInteger() + 5
    start.step 95, 5, {
   		opts.push(["${it}":"${it}%"])
	}
    opts.push(["100":"Fully open"])
    return opts
}

def getLogLevels(){
    return [["0":"None"],["10":"Lite"],["20":"Moderate"],["30":"Detailed"],["40":"Super nerdy"]]
}

def getLogLevel(val){
	def logLvl = 'Lite'
    def l = getLogLevels()
    if (val){
    	logLvl = l.find{ it."${val}"}
        logLvl = logLvl."${val}".value
    }
    return '[' + logLvl + ']'
}

def zoneFixedOptions(){
	def opts = []
    def start
    if (!state.tempScale) state.tempScale = location.temperatureScale
	if (state.tempScale == "F"){
    	start = 60
        start.step 81, 1, {
   			opts.push(["${it}":"${it}°F"])
		}
    } else {
    	start = 15
        start.step 27, 1, {
   			opts.push(["${it}":"${it}°C"])
		}
    }
	return opts
}

def zoneTempOptions(){
	def zo
    if (!state.tempScale) state.tempScale = location.temperatureScale
	if (state.tempScale == "F"){
    	zo = [["8":"8°F"],["5":"5°F"],["4":"4°F"],["3":"3°F"],["2":"2°F"],["1":"1°F"],["0":"0°F"],["-1":"-1°F"],["-2":"-2°F"],["-3":"-3°F"],["-4":"-4°F"],["-5":"-5°F"],["-8":"-8°F"]]
    } else {
    	zo = [["5":"5°C"],["4":"4°C"],["3":"3°C"],["2":"2°C"],["1":"1°C"],["0":"0°C"],["-1":"-1°C"],["-2":"-2°C"],["-3":"-3°C"],["-4":"-4°C"],["-5":"-5°C"]]
    }
	return zo
}

//report methods, called from parent
def getEndReport(){
	return state.endReport ?: "\n\tNo data available yet."
}

def getZoneConfig(){
	//zoneControlSwitch
    def zc = "Not Activated"
    def cspStr = ""
    if (parent.isAC()){
        if (zoneControlType == "fixed") cspStr = "\n\tcooling set point: ${tempStr(settings.staticCSP)}"
        else cspStr = "\n\tcooling offset: ${tempStr(settings.coolOffset)}"
    }
    def hspStr = ""
    if (zoneControlType == "fixed") hspStr = "heating set point: ${tempStr(settings.staticHSP)}"
    else hspStr = "heating offset: ${tempStr(settings.heatOffset)}"
    
    def zt = hspStr + cspStr
    if (zoneControlSwitch) zc = "is ${zoneControlSwitch.currentValue("switch")} via [${zoneControlSwitch.displayName}]"
	return "\n\tVents: ${vents}\n\ttemp sensor: [${tempSensors}]\n\tminimum vent opening: ${minVo}%\n\tmaximum vent opening: ${maxVo}%\n\t${zt}\n\tzone control: ${zc}\n\tversion: ${state.vChild ?: "No data available yet."}"
}

def getZoneState(){
    def s 
    if (state.running == true) s = true
    else s = false
    def qr = false
    if (settings.AggressiveTempVentCurve && state.AggressiveTempVentCurveActive && s) qr = true
    def report =  "\n\trunning: ${s}\n\tqr active: ${qr}\n\tcurrent temp: ${tempStr(state.zoneTemp)}\n\tset point: ${tempStr(state.activeSetPoint)}"
    vents.each{ vent ->
 		def b = vent.currentValue("battery") ? vent.currentValue("battery") + "%" : "No data yet"
        def l = vent.currentValue("level").toInteger()
        
        def d = state."${vent.id}"
        def lrd = "No data yet"
        def rtt = "response time: No data yet"
        if (d){
        	def t = d.voResponseTS
            def r = d.voTTC
            if (t) lrd = (new Date(d.voResponseTS + location.timeZone.rawOffset ).format("yyyy-MM-dd HH:mm")).toString()
            if (r) rtt = "response time: ${r}s"
        }
		report = report + "\n\tVent: ${vent.displayName}\n\t\tlevel: ${l}%\n\t\tbattery: ${b}\n\t\t${rtt}\n\t\tlast response: ${lrd}"
    }
    return report
}

def getZoneTemp(){
	return state.zoneTemp
}
