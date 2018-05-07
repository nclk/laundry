
basedir=$(dirname $0)

usage() {
	echo "
	usage: $0 [container_alias] [--dml]";
}

container_alias=$1
if [ "x$container_alias" == "x" ]; then
	container_alias="laundrydb"
fi

dbhost=`$basedir/docker-ip.py $container_alias`

if [ "x$dbhost" != "x" ]; then
	if [ "x$2" == "xddl" ]; then
		lein ddl $dbhost down 99
		lein ddl $dbhost up
	else
		lein dml $dbhost down
	fi
	lein dml $dbhost up
else
	usage;
fi

