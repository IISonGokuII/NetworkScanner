import math
LON0,LON1,LAT0,LAT1=6.984,7.044,49.331,49.359
OLAT,OLON=49.345,7.014
MX=111320*math.cos(math.radians(OLAT)); MZ=110950
def tx(lon,z): return (lon+180)/360*2**z
def ty(lat,z): r=math.radians(lat); return (1-math.log(math.tan(r)+1/math.cos(r))/math.pi)/2*2**z
def local(lat,lon): return ((lon-OLON)*MX, -(lat-OLAT)*MZ)
