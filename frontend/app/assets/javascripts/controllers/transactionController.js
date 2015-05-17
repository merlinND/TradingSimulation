(function() {
  'use strict';

  angular.module('myApp').controller(
      'TransactionController',
      [
          '$scope',
          '$filter',
          'alertService',
          'transactionList',
          'ngTableParams',
          function($scope, $filter, alertService, transactionList,
              ngTableParams) {
            $scope.alerts = alertService.get();
            var transactions = transactionList.get();

            $scope.$on('transactions:updated', function(event, data) {
              $scope.tableParams.reload();
            });

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
                var orderedData = params.sorting() ? $filter('orderBy')(
                    transactions, params.orderBy()) : transactions;
                $defer.resolve(orderedData);
              }
            });
            /* jshint ignore:end */

          } ]);

})();