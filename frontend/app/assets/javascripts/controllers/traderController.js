(function() {
  'use strict';

  /**
   * Controller responsible for the table of traders in the system
   */
  angular.module('myApp').controller(
      'TraderController',
      [
          '$scope',
          '$filter',
          'traderList',
          'transactionList',
          'ohlcGraphTransactionFlags',
          'ngTableParams',
          function($scope, $filter, traderList, transactionList, ohlcGraphTransactionFlags,
              ngTableParams) {
            var traders = traderList.get();
            var transactions = transactionList.get();

            /**
             * Gets all the transactions for the selected trader ID and attaches
             * them as buy/sell flags to the global OHLC graph
             */
            $scope.showTransactionsOnGraphFor = function(trader) {
              ohlcGraphTransactionFlags.watchTrader(trader);
            };

            /**
             * Updates the ngTable on data change
             */
            $scope.$on('traders:updated', function(event, data) {
              $scope.tableParams.reload();
            });

            /**
             * ngTable configuration We tell jshint to ignore this part since
             * ngTableParams starts with a lowercase letter, which leads to a
             * jshint error
             */
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
                var data = [];
                for ( var report in traders) {
                  data.push(traders[report]);
                }
                var orderedData = params.sorting() ? $filter('orderBy')(
                    data, params.orderBy()) : data;
                $defer.resolve(orderedData);
              }
            });
            /* jshint ignore:end */

          } ]);

})();