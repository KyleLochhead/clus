# Copyright 2021 Province of British Columbia
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
# http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and limitations under the License.
#===========================================================================================

defineModule (sim, list (
  name = "grizzlysurvivalCLUS",
  description = "This module calculates adult female grizzly bear survival rate in grizzly bear population units (GBPUs) by adapting the model developed by Boulanger and Stenhouse (2014).",
  keywords = c ("grizzly bear", "survival", "road density", "adult female"), 
  authors = c (person ("Tyler", "Muhly", email = "tyler.muhly@gov.bc.ca", role = c("aut", "cre")),
               person ("Kyle", "Lochhead", email = "kyle.lochhead@gov.bc.ca", role = c("aut", "cre"))),
  childModules = character (0),
  version = list (SpaDES.core = "0.2.5", grizzlysurvivalCLUS = "0.0.1"),
  spatialExtent = raster::extent (rep (NA_real_, 4)),
  timeframe = as.POSIXlt (c (NA, NA)),
  timeunit = "year",
  citation = list ("citation.bib"),
  documentation = list ("README.md", "grizzlysurvivalCLUS.Rmd"),
  reqdPkgs = list (),
  parameters = rbind (
    #defineParameter("paramName", "paramClass", value, min, max, "parameter description"),
    defineParameter ("calculateInterval", "numeric", 1, 1, 200, "The simulation time at which survival rates are calculated"),
    defineParameter ("roadDensity", "numeric", 10, 0, 100, "This is the road density for a single roaded 1 ha pixel that the user defines. It is necessary to fit the survival model. "),
    defineParameter ("rasterGBPU", "character", "rast.caribou_herd", NA, NA, "Name of the raster of the grizzly bear population unit (GBPU) boundaries raster that is stored in the psql clusdb. Created in Params/grizzly_bear_population_unit.rmd"), # could be included in dataLoader instead for easier use in other modules?
    defineParameter ("tableGBPU", "character", "public.caribou_herd", NA, NA, "The look up table to convert raster values to grizzly bear population unit name labels. Created in Params/grizzly_bear_population_unit.rmd")
  ),
  inputObjects = bind_rows(
    expectsInput (objectName = "clusdb", objectClass = "SQLiteConnection", desc = 'A database that stores dynamic variables used in the model. This module needs the roadyear variable from the pixels table in the clusdb.', sourceURL = NA),
    expectsInput(objectName ="scenario", objectClass ="data.table", desc = 'The name of the scenario and its description', sourceURL = NA),
    expectsInput(objectName ="roads", objectClass ="RasterLayer", desc = 'A raster of the roads; note I put this here to make sure this module is run AFTER the roadCLUS module', sourceURL = NA),
    expectsInput(objectName ="updateInterval", objectClass ="numeric", desc = 'The length of the time period. Ex, 1 year, 5 year', sourceURL = NA)
    ),
  outputObjects = bind_rows(
    createsOutput (objectName = "tableGrizzSurvivalReport", objectClass = "data.table", desc = "A data.table object. Consists of survival rate estimates for each grizzly bear population unit (GBPU) in the study area at each time step. Gets saved in the 'outputs' of the module.")
    )
  )
)

doEvent.grizzlysurvivalCLUS = function (sim, eventTime, eventType) {
  switch (
    eventType,
    init = { # identify GBPUs in the study area, calculate survival rate at time 0 for those GBPUs and save the survival rate estimate
      sim <- Init (sim) # identify GBPUs in the study area and calculate survival rate at time 0; instantiate a table to save the survival rate estimates
      sim <- scheduleEvent (sim, time(sim) + P(sim, "grizzlysurvivalCLUS", "calculateInterval"), "grizzlysurvivalCLUS", "calculateSurvival", 10) # schedule the next survival calculation event; should be after roadCLUS
      #sim <- scheduleEvent (sim, end(sim), "grizzlysurvivalCLUS", "adjustSurvivalTable", 9) 
    },
    
    calculateSurvival = { # calculate survival rate at each time interval 
      sim <- predictSurvival (sim) # this function calculates survival rate
      sim <- scheduleEvent (sim, time(sim) + P(sim, "grizzlysurvivalCLUS", "calculateInterval"), "grizzlysurvivalCLUS", "calculateSurvival", 10) # schedule the next survival calculation event  
    },
    # adjustSurvivalTable ={ # calucalte the total area from which the survival rate applies
    #   sim <- adjustSurvivalTable (sim)
    # },
    
    warning (paste ("Undefined event type: '", current (sim) [1, "eventType", with = FALSE],
                    "' in module '", current (sim) [1, "moduleName", with = FALSE], "'", sep = ""))
  )
  return (invisible (sim))
}

Init <- function (sim) { # this function identifies the GBPUs in the 'study area' creates the survival rate table, calculates survival rate at time = 0, and saves the survival table in the clusdb
  # Added a condition here in those cases where the dataLoaderCLUS has already ran
  if(nrow(data.table(dbGetQuery(sim$clusdb, "PRAGMA table_info(pixels)"))[name == 'gbpu_name',])== 0){
    dbExecute (sim$clusdb, "ALTER TABLE pixels ADD COLUMN gbpu_name character") # add a column to the pixel table that will define the GBPU
  
    gbpubounds <- data.table (c (t (raster::as.matrix ( # clip caribou herd raster by the 'study area' set in dataLoader
                                    RASTER_CLIP2 (tmpRast = paste0('temp_', sample(1:10000, 1)), 
                                      srcRaster = P (sim, "grizzlysurvivalCLUS", "rasterGBPU") , # clip the GBPU boundary raster; defined in parameters, above
                                                  clipper = P (sim, "dataLoaderCLUS", "nameBoundaryFile"),  # by the study area; defined in parameters of dataLoaderCLUS
                                                  geom = P (sim, "dataLoaderCLUS", "nameBoundaryGeom"), 
                                                  where_clause =  paste0 (P (sim, "dataLoaderCLUS", "nameBoundaryColumn"), " in (''", paste(sim$boundaryInfo[[3]], sep = "' '", collapse= "'', ''") ,"'')"),
                                                  conn = NULL)))))
    
    setnames (gbpubounds, "V1", "gbpu_name") # rename the default column name
    gbpubounds [, gbpu_name := as.integer (gbpu_name)] # add the GBPU boundary value from the raster and make the value an integer
    gbpubounds [, pixelid := seq_len(.N)] # add pixelid value
    
    vat_table <- data.table(getTableQuery(paste0("SELECT * FROM ", P(sim)$tableGBPU))) # get the GBPU name attribute table that corresponds to the integer values
    # vat_table <<- vat_table
    # gbpubounds <<- gbpubounds
    gbpubounds <- merge (gbpubounds, vat_table, by.x = "gbpu_name", by.y = "raster_integer", all.x = TRUE) # left join the GBPU name to the integer
    gbpubounds [, gbpu_name := NULL] # drop the integer value 
    
    colnames (gbpubounds) <- c("pixelid", "gbpu_name") # rename the herd boundary column
    setorder (gbpubounds, "pixelid") # this helps speed up processing?
  
    dbBegin (sim$clusdb) # fire up the db and add the herd boundary values to the pixels table 
    rs <- dbSendQuery (sim$clusdb, "Update pixels set gbpu_name = :gbpu_name where pixelid = :pixelid", gbpubounds) 
    dbClearResult (rs)
    dbCommit (sim$clusdb) # commit the new column to the db
  }
  # The following calculates for each GBPU:
    # 1. Number of pixels with a road (roadyear > -1) 
    # 2. Multiplied by the road density parameter (P(sim)$roadDensity)
    # 3. Divided by total area, to calculate GBPU road density
  sim$tableGrizzSurvivalReport <- data.table (dbGetQuery (sim$clusdb, "SELECT SUM (CASE WHEN roadyear > -1 THEN 1 ELSE 0 END) AS total_roaded, COUNT(*) AS total_area, gbpu_name FROM pixels WHERE gbpu_name IS NOT NULL GROUP BY gbpu_name;"))
  sim$tableGrizzSurvivalReport [, road_density := (total_roaded * P(sim)$roadDensity) / total_area] 
  
   # The following equation calculates the survival rate in the herd area using the Boualnger and Stenhouse (2014) model
      # Note that this equation approximates the relationship; waiting on Boulanger for the model parameters 
  
  sim$tableGrizzSurvivalReport [, survival_rate := (1/(1+exp(-3.9+(road_density * 1.06))))]
  sim$tableGrizzSurvivalReport [, c("timeperiod", "scenario", "compartment") := list(time(sim)*sim$updateInterval, sim$scenario$name, sim$boundaryInfo[[3]]) ] # add the time of the survival calc

  #print(sim$tableGrizzSurvivalReport)

  return(invisible(sim))
}


predictSurvival <- function (sim) { # this function calculates survival rate at each time interval; same as on init, above
 
  new_tableGrizzSurvivalReport <- data.table (dbGetQuery (sim$clusdb, "SELECT SUM (CASE WHEN roadyear > -1 THEN 1 ELSE 0 END) AS total_roaded, COUNT(*) AS total_area, gbpu_name FROM pixels WHERE gbpu_name IS NOT NULL GROUP BY gbpu_name;"))
  new_tableGrizzSurvivalReport [, road_density := (total_roaded * P(sim)$roadDensity) / total_area]  
  new_tableGrizzSurvivalReport [, survival_rate := (1/(1+exp(-3.9+(road_density * 1.06))))]
  new_tableGrizzSurvivalReport [, c("timeperiod", "scenario", "compartment") := list(time(sim)*sim$updateInterval, sim$scenario$name,sim$boundaryInfo[[3]]) ] # add the time of the survival calc
  
  
  sim$tableGrizzSurvivalReport <- rbindlist (list(sim$tableGrizzSurvivalReport, new_tableGrizzSurvivalReport)) # bind the new survival rate table to the existing table
  #rm (new_tableSurvivalReport) # is this necessary? -- frees up memory
  return (invisible(sim))
}

# adjustSurvivalTable <- function (sim) { # this function adds the total area of the GBPU + compartment area to be used for weighting in the dashboard
#   total_area<-data.table(dbGetQuery (sim$clusdb, "SELECT count(*) as area, gbpu_name FROM pixels WHERE gbpu_name IS NOT NULL GROUP BY gbpu_name;"))
#   sim$tableGrizzSurvivalReport<-merge(sim$tableGrizzSurvivalReport, total_area, by.x = "gbpu_name", by.y = "gbpu_name", all.x = TRUE )
#   return (invisible(sim))
# }


.inputObjects <- function(sim) {
  #cacheTags <- c(currentModule(sim), "function:.inputObjects") ## uncomment this if Cache is being used
  dPath <- asPath(getOption("reproducible.destinationPath", dataPath(sim)), 1)
  message(currentModule(sim), ": using dataPath '", dPath, "'.")
  return(invisible(sim))
}

