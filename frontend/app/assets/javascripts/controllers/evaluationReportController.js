(function() {
  'use strict';

  angular.module('myApp').controller(
      'EvaluationReportController',
      [
          '$scope',
          '$filter',
          'traderList',
          'ngTableParams',
          'ohlcGraphTransactionFlags',
          function($scope, $filter, traderList, ngTableParams, ohlcGraphTransactionFlags) {
            var traders = traderList.get();
            var evaluationReports = {};

            /**
             * Gets all the transactions for the selected trader ID and attaches
             * them as buy/sell flags to the global OHLC graph
             */
            $scope.showTransactionsOnGraphFor = function(traderId) {
              ohlcGraphTransactionFlags.watchTrader(traders[traderId]);
            };

            
            /**
             * Listens to EvaluationReport messages
             */
            var ws = new WebSocket(
                'ws://localhost:9000/trader/evaluation-report');

            ws.onmessage = function(event) {
              var report = JSON.parse(event.data);
              evaluationReports[report.traderId] = report;

              if (!$scope.referenceCurrency) {
                $scope.referenceCurrency = report.currency.s;
              }
              $scope.tableParams.reload();
            };


            /**
             * ngTable configuration We tell jshint to ignore this part since
             * ngTableParams starts with a lowercase letter, which leads to a
             * jshint error
             */
            /* jshint ignore:start */
            $scope.tableParams = new ngTableParams({
              count : evaluationReports.length, // no pager
              sorting : {
                totalReturns : 'desc'
              }
            }, {
              counts : [], // hide page size
              total : evaluationReports.length,
              getData : function($defer, params) {
                var data = [];
                for ( var report in evaluationReports) {
                  data.push(evaluationReports[report]);
                }
                var orderedData = params.sorting() ? $filter('orderBy')(data,
                    params.orderBy()) : data;
                $defer.resolve(orderedData);
              }
            });
            /* jshint ignore:end */

          } ]);

})();