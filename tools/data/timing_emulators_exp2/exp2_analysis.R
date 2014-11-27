library(jsonlite)
library(ggplot2)

# Read in tasks from VT-x emulator
x = readLines("_tasks.json")
tasks = fromJSON(paste('[', paste(x, collapse=','), ']'))
tasks$task = as.factor(tasks$task)
tasks$etype="VT-x"

# Read in tasks from None 
x = readLines("_tasks.json")
tasks2 = fromJSON(paste('[', paste(x, collapse=','), ']'))
tasks2$task = as.factor(tasks$task)
tasks2$etype="None"

# Combine two 
tasks_all = rbind(tasks,tasks2)
tasks_all$etype = as.factor(tasks_all$etype)

formatter100 <- function(x){ 
  x/1000
}
ggplot(tasks_all, aes(x=task, y=duration, fill=etype, color=etype)) + 
  geom_boxplot(outlier.size=1, notch = T) + 
  theme_bw() + 
  scale_y_continuous(labels = formatter100) + 
  ylab("Duration (seconds)") + 
  scale_fill_discrete(name="Acceleration") + 
  scale_color_discrete(guide=FALSE) + 
  xlab("Task") + 
  theme(legend.justification=c(1, 1), legend.position=c(1, 1)) + 
  theme(axis.text=element_text(size=12)) + 
  theme(axis.title=element_text(size=14)) + 
  # Omits two outliers, (None,install,15.5 sec) 
  # and (None,keypress,13 sec) 
  coord_cartesian(ylim = c(0,6000))
  
  

ggplot(tasks_all, aes(x=task, y=duration, color=etype)) + 
  geom_point() + geom_jitter()