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
//              var flagsData = [];
//              transactions.map(function(t) {
//                if (t.buyerId == trader.id || t.sellerId == trader.id) {
//                  var title = (t.buyerId == trader.id) ? 'B' : 'S';
//                  flagsData.push({
//                    x : t.timestamp,
//                    title : title
//                  });
//                }
//              });
//
//              var flagSeriesId = "activeTraderFlags";
//              var flagSeries = chartSeries.filter(function(series) {
//                return series.id == flagSeriesId;
//              })[0];
//
//              if (!flagSeries) {
//                flagSeries = {
//                  type : 'flags',
//                  id : flagSeriesId,
//                  name: "Transactions of " + trader.name,
//                  shape : 'circlepin',
//                  color : '#df691a',
//                  fillColor : '#df691a',
//                  onSeries : chartSeries[0].id, // TODO: handle multiple series
//                  style : {
//                    color : 'white'
//                  },
//                  width: 16,
//                  data : []
//                };
//                chartSeries.push(flagSeries);
//              }
//
//              flagSeries.data = flagsData;

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
                var orderedData = params.sorting() ? $filter('orderBy')(
                    traders, params.orderBy()) : traders;
                $defer.resolve(orderedData);
              }
            });
            /* jshint ignore:end */

          } ]);

})();