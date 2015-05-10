(function() {
  'use strict';

  angular.module('myApp').controller('TransactionController', [ '$scope', 'alertService',
      function($scope, alertService) {
        $scope.alerts = alertService.get();
        $scope.transactions = [];

        var ws = new WebSocket('ws://localhost:9000/market/transaction');

        ws.onmessage = function(event) {
          var transaction = JSON.parse(event.data);
          $scope.$apply(function() {
            if (transaction.buyerId === -1) {
              transaction.buyerId = 'external';
            } else if (transaction.sellerId === -1) {
              transaction.sellerId = 'external';
            }
            console.log(transaction);
            
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