import os
import subprocess
import sys
import time
import unified_format_parser

start = time.time()

# get arguments
repo  = sys.argv[1]
date1 = sys.argv[2]
date2 = sys.argv[3]

# change to repo
os.chdir(repo)

# get result
try: 
	diff_detail = subprocess.check_output("cvs diff -N -u -D " + date1 + " -D " + date2).decode('ISO-8859-1')
except subprocess.CalledProcessError as e:
	diff_detail = e.output.decode('ISO-8859-1')

# parse result
unified_format_parser.parse_unified_format(diff_detail)

end = time.time()
#print(str(end - start))