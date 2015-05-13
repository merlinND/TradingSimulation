(function() {
  'use strict';

  angular.module('myApp').controller(
      'TransactionController',
      [ '$scope', 'alertService', 'traderList',
          function($scope, alertService, traderList) {
            $scope.alerts = alertService.get();
            $scope.transactions = [];
            $scope.traders = traderList.get();

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
                
                $scope.transactions.push(transaction);
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

          } ]);

})();