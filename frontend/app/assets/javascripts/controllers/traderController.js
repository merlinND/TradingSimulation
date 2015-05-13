(function() {
  'use strict';

  angular.module('myApp').controller('TraderController', [ '$scope', 'traderList',
      function($scope, traderList) {
        $scope.traders = traderList.get();
      } ]);

})();