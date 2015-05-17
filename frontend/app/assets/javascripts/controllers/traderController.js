(function() {
  'use strict';

  angular.module('myApp').controller(
      'TraderController',
      [
          '$scope',
          '$filter',
          'traderList',
          'ngTableParams',
          function($scope, $filter, traderList, ngTableParams) {
            var traders = traderList.get();

            /* jshint ignore:start */
            $scope.tableParams = new ngTableParams({
              count : traders.length, // no pager
              sorting : {
                id : 'asc'
              }
            }, {
              counts : [], // hide page size
              total : traders.length,
              getData : function($defer, params) {
                var orderedData = params.sorting() ? $filter('orderBy')(
                    traders, params.orderBy()) : traders;
                $defer.resolve(orderedData);
              }
            });
            /* jshint ignore:end */

          } ]);

})();