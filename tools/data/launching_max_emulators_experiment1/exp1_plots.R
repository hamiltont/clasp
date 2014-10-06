setwd("~/remoteClasp/logs/2014_10_03-16_13-EDT")
library(jsonlite)


###################################################
#
#  Choose: Either read in one emulatormanager
#          or multiple using glob. This is multiple,
#          read/plot one is below
#
####################################################

# Read one
x = readLines("_emulatormanager.json")
emulators = fromJSON(paste('[', paste(x, collapse=','), ']'))

# Read them all into one DF
trial = 0
result = data.frame()
for (file in Sys.glob("*/_emulatormanager.json")) {
  x = readLines(file)
  innerresult = fromJSON(paste('[', paste(x, collapse=','), ']'))
  innerresult$trial = trial
  trial = trial + 1
  if (length(result) == 0)
    result = innerresult
  else 
    result = rbind(result, innerresult)
}
result$trial = as.factor(result$trial)
emulators = result

# Translate raw time into seconds of difference
emulators$asOf = as.POSIXct(emulators$asOf,format="%Y-%m-%dT%H:%M:%OS")
emulators$exp_seconds = emulators$asOf - min(emulators$asOf)

# Simple plot
plot(emulators$emulators, emulators$boottime/1000, type='l',
     ylab="Boot Time (sec)", xlab="Active Emulators When Booting")

# Get lm string to print on graph
# From http://stackoverflow.com/a/13451587/119592
lm_eqn = function(m) {
  l <- list(a = format(coef(m)[1], digits = 2),
            b = format(abs(coef(m)[2]), digits = 2),
            r2 = format(summary(m)$r.squared, digits = 3));
  if (coef(m)[2] >= 0)  {
    eq <- substitute(italic(y) == a + b %.% italic(x)*","~~italic(r)^2~"="~r2,l)
  } else {
    eq <- substitute(italic(y) == a - b %.% italic(x)*","~~italic(r)^2~"="~r2,l)    
  }
  as.character(as.expression(eq));                 
}
emulators$x = emulators$emulators
emulators$y = emulators$boottime/1000
library(ggplot2)

# Plot one emulator list
ggplot(emulators, aes(x=emulators, y=boottime/1000)) + 
  geom_point() + 
  ylab("Boot Time (seconds)") + 
  xlab("Emulators Launched") + 
  geom_smooth(method="lm", formula=y ~ x) + 
  scale_x_discrete(breaks=seq(0,70,5)) +
  annotate("text", x=25, y=250, label = lm_eqn(lm(y~x,emulators)), size = 6, parse=TRUE)

# Plot multiple emulator lists with nice error bars
e_count = seq(0,63)
boot_mean = as.integer(by(emulators$boottime/1000, emulators$emulators, mean))
boot_se = as.double(by(emulators$boottime/1000, emulators$emulators, function(x) sd(x)/sqrt(length(x))))
combined_emulators = data.frame(e_count, boot_mean, boot_se)
ggplot(combined_emulators, aes(x=e_count, y=boot_mean)) + 
  geom_point() + 
  geom_errorbar(aes(ymax = boot_mean + boot_se, ymin=boot_mean - boot_se)) + 
  ylab("Boot Time (seconds)") + 
  xlab("Emulators Launched") + 
  geom_smooth(method="lm", formula=y ~ x) + 
  scale_x_discrete(breaks=seq(0,70,5)) +
  annotate("text", x=25, y=250, label = lm_eqn(lm(y~x,emulators)), size = 6, parse=TRUE)

###################################################
#
#         Below here is plotting node metrics
#
####################################################

node_file = Sys.glob("_node_*_metrics.json")[1]
cat("Reading node metrics from:", node_file)
metrics = readLines(Sys.glob("_node_*_metrics.json"))

# Remove last line as there is sometimes a file writing problem and this 
# is truncated without a complete JSON object
metrics = head(metrics,-1)

# Convert from JSON (date conversion is off by about 3 hours for some reason)
metrics = fromJSON(paste('[', paste(metrics, collapse=','), ']'))
metrics$asOf = as.POSIXct(metrics$asOf,format="%Y-%m-%dT%H:%M:%OS")

# Split into separate data frames
swap = subset(metrics,!is.na(pageOut))
cpu = subset(metrics,!is.na(nice))
ram = subset(metrics,!is.na(ram))

# Used for date breaking and formatting functions
library(scales)

# Used for grid.arrange
library("gridExtra")

p_ram = ggplot(ram, aes(x=asOf, y=freePercent)) + 
  geom_line() + 
  ylab("% Free") + 
  ggtitle("RAM") +
  theme(axis.title.x = element_blank()) + 
  theme(axis.ticks.x = element_blank(), axis.text.x = element_blank()) +   
  theme(plot.title = element_text(size=16)) + 
  theme(axis.title.y = element_text(size=14)) + 
  theme(plot.margin=unit(c(0,5,-3,5), "mm")) + 
  scale_x_datetime(breaks = date_breaks("1 hour"), labels = date_format("%H"))

scaled_start = strptime(as.character(swap$asOf[100]), format="%Y-%m-%d %H:%M:%S")
scaled_end = strptime(as.character(swap$asOf[350]), format="%Y-%m-%d %H:%M:%S")
scaled_ticks=seq(scaled_start, by="2 min", scaled_end)
scaled_labels = as.integer(as.character(scaled_ticks, format="%M")) - 24
p_ram_scaled = p_ram + 
  coord_cartesian(xlim = c(swap$asOf[100], swap$asOf[350]),
                  ylim = c(93,100)) + 
  annotate("rect", xmin=swap$asOf[105], xmax=swap$asOf[153], 
           ymin=96.5, ymax=98.5, fill = "blue", alpha=0.15) + 
  annotate("rect", xmin=swap$asOf[200], xmax=swap$asOf[245], 
           ymin=95, ymax=97, fill = "blue", alpha=0.15) + 
  annotate("rect", xmin=swap$asOf[295], xmax=swap$asOf[345], 
           ymin=93.5, ymax=95.5, fill = "blue", alpha=0.15) + 
  theme(plot.margin=unit(c(0,5,-3,1), "mm")) + 
  scale_x_datetime(breaks = scaled_ticks)

formatter100 <- function(x){ 
  x*100
}

cpu_cols <- c("System"="green","Wait"="blue","User"="black")
p_cpu = ggplot(cpu, aes(x=asOf)) + 
  geom_line(aes(y=user, alpha=user, color="User")) + 
  geom_line(aes(y=sys, color="System")) + 
  geom_line(aes(y=wait, color="Wait")) + 
  scale_alpha(range = c(0.5, 0), guide=F) + 
  scale_color_manual(name="CPU Metric", values=cpu_cols) + 
  ylab("% Total CPU") + 
  ggtitle("CPU") +
  theme(axis.ticks.x = element_blank(), axis.text.x = element_blank()) + 
  theme(axis.title.x = element_blank()) + 
  theme(plot.title = element_text(size=16)) + 
  theme(axis.title.y = element_text(size=14)) + 
  # theme(legend.position="none") + # Get beautiful alpha w/o legend
  theme(legend.justification=c(0, 1), legend.position=c(0, 1)) + 
  theme(plot.margin=unit(c(-3,5,-3,5), "mm")) + 
  scale_y_continuous(labels = formatter100) + 
  scale_x_datetime(breaks = date_breaks("1 hour"), labels = date_format("%H"))

p_cpu_scaled = p_cpu + 
  coord_cartesian(xlim = c(swap$asOf[100], swap$asOf[350]),
                  ylim = c(0,0.06)) + 
  geom_line(aes(y=user, color="User")) + 
  theme(axis.text.x = element_text(vjust = 1)) + 
  theme(axis.title.x = element_text(size=14)) + 
  xlab("Minutes") + 
  theme(plot.margin=unit(c(-3,5,0,5), "mm")) + 
  scale_x_datetime(breaks = scaled_ticks, 
                   labels = scaled_labels)

# Add points to indicate heartbeats
beats = seq(111, 350, 12)
for (beat in beats) {
  p_cpu_scaled = p_cpu_scaled + 
    annotate("point", size=4, fill="red", alpha=0.4, shape=25,
             x = swap$asOf[beat], y=0.014) +
    annotate("point", size=4, fill="red", alpha=0.4, shape=24,
             x = swap$asOf[beat], y=0.035) + 
    annotate("segment", x = swap$asOf[beat], xend = as.integer(swap$asOf[beat]),
             y = 0.015, yend = 0.034, alpha=0.4, colour = "red") 
    # Useful if you need the indexes
    # annotate("text", x = swap$asOf[beat], y=0.02, 
    #         label=as.character(beat))
}

grid.arrange(p_ram_scaled, p_cpu_scaled, nrow=2, ncol=1)

# Cheap hack to make time start from 00 instead of 06
swap$exp_time = swap$asOf - 60*60*6

p_swap = ggplot(swap, aes(x=exp_time, y=pageOut)) + 
  geom_line() + 
  ylab("Page Outs") + 
  xlab("Experiment Hours") + 
  ggtitle("Swap") +
  theme(plot.title = element_text(size=16)) + 
  theme(axis.title.y = element_text(size=14)) +
  theme(axis.title.x = element_text(size=14)) +
  theme(plot.margin=unit(c(-3,5,0,1), "mm")) + 
  scale_x_datetime(breaks = date_breaks("1 hour"), labels = date_format("%H"))

grid.arrange(p_ram, p_cpu, p_swap, nrow=3, ncol=1)
