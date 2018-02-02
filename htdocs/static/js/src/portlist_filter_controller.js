define(function(require) {

    var URI = require('libs/urijs/URI');
    require('libs/select2.min');

    var selectors = {
        ifclassfilter: '#ifclassfilter',
        queryfilter: '#queryfilter',
        netboxfilter: '#netbox-filter',
        operstatusfilter: '#operstatus-filter',
        filterForm: '#filters'
    };

    /* Create url based on filter functions. Each filter is responsible for
       creating a parameter and value */
    function addFilterParameters(uri) {
        filters = [netboxFilter, ifClassFilter, queryFilter, linkFilter];
        uri.addSearch(filters.reduce(function(obj, func) {
            return Object.assign(obj, func());
        }, {}));
        console.log(uri.toString());
        return uri;
    }

    function linkFilter() {
        return { ifoperstatus: $(selectors.operstatusfilter).val() }
    }

    function netboxFilter() {
        var value = $(selectors.netboxfilter).val();
        return value ? { netbox: value.split(',') } : {};
    }

    function ifClassFilter() {
        return { ifclass: $(selectors.ifclassfilter).val() }
    }

    function queryFilter() {
        var search = $(selectors.queryfilter).val()
        return search ? { search: search } : search;
    }

    /** Adds select2 component to netboxfilter */
    function addNetboxFilter() {
        var netboxFilter = $(selectors.netboxfilter).select2({
            ajax: {
                url: '/api/netbox/',
                dataType: 'json',
                quietMillis: 500,
                data: function(term, page) {
                    return { search: term }
                },
                results: function(data, page) {
                    return {results: data.results.map(function(d) {
                        return {text: d.sysname, id: d.id}
                    })};
                }
            },
            multiple: true,
            minimumInputLength: 2,
            width: 'off'
        });
    }

    /* Reloads data from the api */
    function reload(table) {
        table.ajax.url(getUrl()).load();
    }

    /* Adds listeners for reloading when the filters change */
    function reloadOnFilterChange(table) {
        var reloadInterval = 500  // ms
        var _reload = reload.bind(this, table)
        var throttledReload = _.throttle(_reload, reloadInterval, {leading: false});

        $(selectors.filterForm).on('change', _reload);
        $(selectors.filterForm).on('keyup', '#queryfilter', function(event) {
            var isWordCharacter = event.key.length === 1;
            var isBackspaceOrDelete = (event.keyCode === 8 || event.keyCode === 46);
            if (isWordCharacter || isBackspaceOrDelete) {
                throttledReload();
            }
        });
    }

    /* Initialize everything with given config */
    function filterController(table) {
        addNetboxFilter();
        reloadOnFilterChange(table);
    }

    /* Creates the url for fetching data from the API*/
    function getUrl() {
        var baseUri = URI("/api/1/interface/");
        var uri = addFilterParameters(baseUri);
        return uri.toString();
    }

    return {
        controller: filterController,
        getUrl: getUrl
    };


});