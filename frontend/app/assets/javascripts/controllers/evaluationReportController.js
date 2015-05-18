(function() {
  'use strict';

  angular.module('myApp').controller(
      'EvaluationReportController',
      [
          '$scope',
          '$filter',
          'traderList',
          'ngTableParams',
          function($scope, $filter, traderList, ngTableParams) {
            $scope.traders = traderList.get();
            var evaluationReports = {};

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