(function() {
  'use strict';

  angular.module('myApp').controller('EvaluationReportController', [ '$scope', 'traderList',
      function($scope, traderList) {
        $scope.traders = traderList.get();
        $scope.evaluationReports = {};
        
        var ws = new WebSocket('ws://localhost:9000/trader/evaluation-report');

        ws.onmessage = function(event) {
          var report = JSON.parse(event.data);
          $scope.evaluationReports[report.traderId] = report;
          
          if (!$scope.referenceCurrency) {
            $scope.referenceCurrency = report.currency.name;
          }
        };
        
      } ]);

})();