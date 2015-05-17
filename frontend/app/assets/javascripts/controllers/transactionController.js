(function() {
  'use strict';

  angular.module('myApp').controller(
      'TransactionController',
      [
          '$scope',
          '$filter',
          'alertService',
          'traderList',
          'ngTableParams',
          function($scope, $filter, alertService, traderList, ngTableParams) {
            $scope.alerts = alertService.get();
            $scope.traders = traderList.get();
            var transactions = [];

            var ws = new WebSocket('ws://localhost:9000/market/transaction');

            ws.onmessage = function(event) {
              var transaction = JSON.parse(event.data);
              $scope.$apply(function() {

                if (transaction.buyerId >= 0) {
                  traderList.add(transaction.buyerId);
                } else {
                  transaction.buyerId = 'external';
                }

                if (transaction.sellerId >= 0) {
                  traderList.add(transaction.sellerId);
                } else {
                  transaction.sellerId = 'external';
                }

                transactions.push(transaction);
                $scope.tableParams.reload();
              });
            };

            ws.onclose = function(event) {
              $scope.$apply(function() {
                alertService.add('info', 'Closed connection to the backend');
              });
            };

            ws.onerror = function(event) {
              $scope.$apply(function() {
                alertService.add('danger', 'Lost connection to the backend');
              });
            };

            /* jshint ignore:start */
            $scope.tableParams = new ngTableParams({
              count : transactions.length, // no pager
              sorting : {
                timestamp : 'desc'
              }
            }, {
              counts : [], // hide page size
              total : transactions.length,
              getData : function($defer, params) {
                var orderedData = params.sorting() ? $filter('orderBy')(transactions,
                    params.orderBy()) : transactions;
                $defer.resolve(orderedData);
              }
            });
            /* jshint ignore:end */

          } ]);

})();