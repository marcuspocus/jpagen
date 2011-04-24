# Here you can create play commands that are specific to the module, and extend existing commands
import os, os.path
import getopt
import sys
import subprocess

MODULE = "jpagen"

# Commands that are specific to your module

COMMANDS = ["jpagen:help","jpagen:generate","jpagen:create-list"]

HELP = {
    "jpagen:help": "Show help for this module"
}

def execute(**kargs):
    command = kargs.get("command")
    app = kargs.get("app")
    args = kargs.get("args")
    env = kargs.get("env")
    
    if command == "jpagen:help":
		print "~ Help for jpagen"
		print "~ jpagen:generate => Generate Entities + Composite Keys from database using custom file (if conf/table_list.conf exists) or metadata"
		print "~ jpagen:create-list => Create the conf/table_list.conf file from database using metadata"
		print "~"

    if command == "jpagen:generate":
        print "~ Generating Entities + Composite Keys from the database"
        print "~ "
        java_cmd = app.java_cmd([], None, "play.modules.jpagen.Generator", args)
        try:
            subprocess.call(java_cmd, env=os.environ)
        except OSError:
            print "Could not execute the java executable, please make sure the JAVA_HOME environment variable is set properly (the java executable should reside at JAVA_HOME/bin/java). "
            sys.exit(-1)
        print

    if command == "jpagen:create-list":
        print "~ Generating the conf/table_list.conf file from database using metadata"
        print "~ "
        java_cmd = app.java_cmd([], None, "play.modules.jpagen.ListGenerator", args)
        try:
            subprocess.call(java_cmd, env=os.environ)
        except OSError:
            print "Could not execute the java executable, please make sure the JAVA_HOME environment variable is set properly (the java executable should reside at JAVA_HOME/bin/java). "
            sys.exit(-1)
        print

# This will be executed before any command (new, run...)
def before(**kargs):
    command = kargs.get("command")
    app = kargs.get("app")
    args = kargs.get("args")
    env = kargs.get("env")


# This will be executed after any command (new, run...)
def after(**kargs):
    command = kargs.get("command")
    app = kargs.get("app")
    args = kargs.get("args")
    env = kargs.get("env")

    if command == "new":
        pass

