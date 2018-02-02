define(function(require) {

    var URI = require('libs/urijs/URI');
    var Moment = require('moment');
    require('libs/jquery.sparkline');

    function isEmpty(cell) {
        return $(cell.node()).is(':empty');
    }

    /** Adds sparklines to the cells in this column on the current page

       - For each cell, fetch the metrics for that interface.
       - Then find and modify the url for the metric/suffix we are looking for.
       - Use this url to fetch data.
       - Create a sparkline showing these data.

     */
    function addSparklines(table, column, suffix) {
        table.cells(null, column, {page: 'current'}).every(function() {
            var cell = this;
            if (isEmpty(cell)) {
                var fetchMetrics = $.getJSON('/api/interface/' + getInterfaceId(table, this) + '/metrics/');
                fetchMetrics
                    .then(function(metrics) {
                        return getGraphiteUri(metrics, suffix)[0];
                    })
                    .then(function(uri) {
                        return uri ? $.getJSON(uri.toString()) : [];
                    })
                    .then(function(response) {
                        response.forEach(function(data) {
                            createSparkLine(createSparkContainer(cell), convertToSparkLine(data));
                        });
                    })
            }
        });
    }

    /* Gets the interface id from the row-data of this cell */
    function getInterfaceId(table, cell) {
        return table.row(cell.index().row).data().id;
    }

    /*
     * Finds the correct url based on the suffix and modifies it for fetching data
     */
    function getGraphiteUri(metrics, suffix) {
        return metrics.filter(function(m) {
            return m.suffix === suffix;
        }).map(function(m) {
            return new URI(m.url)
                .removeSearch(['height', 'width', 'template', 'vtitle'])
                .addSearch('format', 'json');
        });
    }

    /* Creates a container for a sparkline inside a cell */
    function createSparkContainer(cell) {
        var $cell = $(cell.node());
        var $container = $('<div>').addClass('sparkline');
        $cell.append($container);
        return $container;
    }

    /* Maps data from graphite to format jquery.sparkline understands */
    function convertToSparkLine(data) {
        return data.datapoints.map(function(point) {
            return [point[1], Number(point[0]).toFixed()];
        });
    }

    function createSparkLine($container, dataPoints) {
        $container.sparkline(dataPoints, {
            tooltipFormatter: self.formatter,
            type: 'line',
            width: '100px'
        });
    }


    /* Adds last used to a column */
    function addLastUsed(table, column) {
        table.cells(null, column, {page: 'current'}).every(function() {
            var cell = this;
            if (isEmpty(cell)) {
                var fetchLastUsed = $.getJSON('/api/interface/' + getInterfaceId(table, cell) + '/last_used/');
                fetchLastUsed.then(function(response) {
                    var hasLink = table.row(cell.index().row).data().ifoperstatus === 1;
                    var timestamp = response.last_used ? Moment(response.last_used) : null;
                    if ((timestamp && timestamp.year() === 9999) || hasLink) {
                        cell.node().innerHTML = 'In use';
                    } else if (timestamp) {
                        cell.node().innerHTML = Moment().format('YYYY-MM-DD HH:mm:ss');
                    }
                });
            }
        });
    }

    /** Check if we need to update columns with dynamic content

       Dynamic content is data that is not gotten directly from the API-query,
       but need to be inserted in a custom way.  */
    function checkDynamicColumns(table) {

        // Define what columns are dynamic and the action to take
        var columnActions = {
            'traffic-ifoutoctets:name': function() {
                addSparklines(table, 'traffic-ifoutoctets:name', 'ifOutOctets');
            },
            'traffic-ifinoctets:name': function() {
                addSparklines(table, 'traffic-ifinoctets:name', 'ifInOctets');
            },
            'last_used:name': function() {
                addLastUsed(table, 'last_used:name')
            }
        }

        for (var selector in columnActions) {
            var column = table.column(selector);
            if (column.visible()) {
                // If the column is visible, execute the action.
                columnActions[selector]();
            }
        }
    }

    function updateDynamicColumns(table) {
        // Update columns when the user toggles a column on/off
        table.on('column-visibility.dt', function(e, settings, column, state) {
            checkDynamicColumns(table);
        })

        // Update columns when the table is (re)drawn
        table.on('draw.dt', function() {
            checkDynamicColumns(table);
        });
    }

    function controller(table) {
        updateDynamicColumns(table);
    }

    return controller;

});