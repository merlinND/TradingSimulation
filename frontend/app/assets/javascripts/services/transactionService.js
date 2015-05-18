(function() {
  'use strict';

  var app = angular.module('myApp');

  angular.module('myApp').factory('transactionList',
      [ '$rootScope', 'traderList', function($rootScope, traderList) {
        var service = {};
        var transactions = [];

        var ws = new WebSocket('ws://localhost:9000/market/transaction');

        ws.onmessage = function(event) {
          var transaction = JSON.parse(event.data);
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
          $rootScope.$broadcast('transactions:updated', transaction);
        };

        service.get = function() {
          return transactions;
        };

        return service;
      } ]);

})();